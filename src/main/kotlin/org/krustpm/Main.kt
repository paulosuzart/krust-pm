package org.krustpm

import kotlin.platform.platformStatic
import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.strands.Strand
import co.paralleluniverse.strands.Strand.UncaughtExceptionHandler
import co.paralleluniverse.strands.SuspendableRunnable
import co.paralleluniverse.actors.ActorRef
import co.paralleluniverse.fibers.SuspendExecution
import co.paralleluniverse.actors.behaviors.ProxyServerActor

import java.lang.Runnable
import java.io.File

import org.zeroturnaround.exec.InvalidExitValueException
import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.StartedProcess

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

import spark.SparkBase.*
import spark.Spark.*
import spark.Request
import spark.Response

import com.google.gson.Gson
import com.google.common.primitives.Ints


enum class ProcessStatus() {
    Started,
    Running,
    Done,
    RetriesExceeded
}



class ProcessManager() : ProxyServerActor("krust-pm", true),
                         ProcessManagerTrait {
  val processes = hashMapOf<String, ManagedProcessTrait>()

  throws(SuspendExecution::class)
  override public fun manage(process : ManagedProcessTrait) {
      this.processes[process.getName()] = process
  }

  throws(SuspendExecution::class)
  override public fun startAll() {
    for ((k, v) in this.processes) {
      v.scale(null)
    }
  }

  throws(SuspendExecution::class)
  override public fun scale(name : String, to : Int) : Int {
    return this.processes[name]?.scale(to) ?: 0
  }

  throws(SuspendExecution::class)
  override public fun getStatus() = this.processes.map {it.value.getStatus()}
}

public data class ManagedProcessJson(val name : String,
                                     val cmd : String,
                                     val status : ProcessStatus,
                                     val totalInstances : Int,
                                     val instances : List<ManagedProcessInstanceJson>,
                                     val env : Map<String, String>)

public data class ManagedProcessInstanceJson(val id : Int,
                                             val currentTry : Int,
                                             val status : ProcessStatus)


class ManagedProcess(private val name : String,
                     private val cmd : String,
                     private val maxRetries : Int,
                     private var initScale : Int,
                     private val env : Map<String, String>,
                     private val cwd : File) :
                     ProxyServerActor(name, true), ManagedProcessTrait {

  var instanceCount : Int = 0
  var currentTry : Int = 0
  val instances = arrayListOf<ManagedProcess.Instance>()
  var processStatus =  ProcessStatus.Started
  val logger = LoggerFactory.getLogger(javaClass<ManagedProcess>())

  init {
    MDC.put("process", name)
  }

  throws(SuspendExecution::class)
  override public fun getName() = this.name

  throws(SuspendExecution::class)
  protected fun scaleUp(to : Int) {
    logger.debug("Scaling up to $to")
    for (i in 1..to) {
        this.instances.add(Instance(instanceCount++))
    }
  }

  throws(SuspendExecution::class)
  protected fun scaleDown(to : Int) {
    logger.debug("Scaling down to $to")
    this.instances.take(to).map {
      this.logger.info("Killing instance ${it.id}")
      it.kill()
      this.instances.remove(it)
    }
  }

  throws(SuspendExecution::class)
  override fun scale(to : Int?) : Int {
    val verified = to ?: this.initScale

    val _to = if (verified < 1) { 1 } else { verified }

    this.logger.debug("Scaling to $_to")
    var scaled = 0
    if (this.instances.size() < _to) {
      scaled = _to - this.instances.size()
      this.scaleUp(scaled)
    } else if (this.instances.size() > _to) {
      scaled = this.instances.size() - _to
      this.scaleDown(scaled)
    }
    return scaled
  }


  throws(SuspendExecution::class)
  override public fun getStatus() : ManagedProcessJson {
    val i = this.instances.map { it.getStatus() }
    return ManagedProcessJson(
            name = this.name,
            cmd = this.cmd,
            status = this.processStatus,
            totalInstances = this.instances.size(),
            instances = i,
            env = this.env
          )
    }

    override fun toString() = """
      Managed Process: ${this.name}
      Initial Instances: ${this.initScale}
      Environment: ${this.env}
      Work Directory: ${this.cwd}
      """


    inner class Instance(val id : Int) {
      val strand : Strand
      var currentTry : Int = 0
      var processStatus =  ProcessStatus.Started
      var process : StartedProcess? = null
      val logger = LoggerFactory.getLogger(javaClass<ManagedProcess>())

      throws(SuspendExecution::class)
      init {
        val thread = Thread(Runnable {
          MDC.put("process", "${this@ManagedProcess.name}-$id")
          this.processStatus = ProcessStatus.Running
          while(true) {
            this.currentTry = this.currentTry + 1

            this.process = ProcessExecutor()
                  .commandSplit(this@ManagedProcess.cmd)
                  .redirectOutput(System.out)
                  .info(logger)
                  .environment(this@ManagedProcess.env)
                  .directory(this@ManagedProcess.cwd)
                  .start()

            logger.info("started")
            val result = this.process!!.getFuture().get()
            if (result.getExitValue() == 0) {
              logger.info("finished at try ${this.currentTry}")
              this.processStatus = ProcessStatus.Done
              break
            } else {
              logger.info("finished with error")
              if (this@ManagedProcess.maxRetries == 0) {
                continue
              }
              if (this.currentTry == this@ManagedProcess.maxRetries) {
                logger.info("No more retries left")
                this.processStatus = ProcessStatus.RetriesExceeded
                break
              }
            }
          }
        })
        this.strand = Strand.of(thread)
        val l = this.logger
        this.strand.setUncaughtExceptionHandler(UncaughtExceptionHandler {f, e ->
          l.info("Instance killed!!!")
        })
        this.strand.start()
      }

      throws(SuspendExecution::class)
      public fun getStatus() : ManagedProcessInstanceJson =
        ManagedProcessInstanceJson(this.id, this.currentTry, this.processStatus)

      throws(SuspendExecution::class)
      public fun kill() {
        this.strand.interrupt()
        this.process?.getProcess()?.destroy()
      }
    }
}



public object Main {

    val DEFAULT_CONFIG_FILE = "./krust-pm.toml"
    val CFG_SERVER_NAME     = "server_name"
    val CFG_SERVER_PORT     = "server_port"
    val CFG_MAX_RETRIES     = "max_retries"
    val CFG_INSTANCES       = "instances"
    val CFG_LOG_DIR         = "log_dir"
    val CFG_PROCESS_NAME    = "name"
    val CFG_CMD             = "cmd"
    val CFG_PROCESSES       = "processes"
    val CFG_ENV             = "env"
    val CFG_DEBUG_CFG       = "debug_config"
    val CFG_CWD             = "work_dir"

    platformStatic public fun main(vararg args: String) {

      val file = if (args.count() == 0) { Main.DEFAULT_CONFIG_FILE } else { args[0] }
      val toml = parseConfig(File(file))
      val kpm = ProcessManager().spawn() as ProcessManagerTrait

      loadProcesses(toml).forEach {
        kpm.manage(it.spawn() as ManagedProcessTrait)
      }

      kpm.startAll()
      val gson = Gson()

      ipAddress(toml.getString(CFG_SERVER_NAME))
      port(Ints.checkedCast(toml.getLong(CFG_SERVER_PORT)))


      get("/", {req, res ->
          kpm.getStatus()
        },
        { gson.toJson(it) }
      )

      get("/ps/scale", {req, res ->
        // TODO: Handle Exception
          val name = req.queryParams("name")
          val to = req.queryParams("to")
          kpm.scale(name, Integer.valueOf(to))
        },
        { gson.toJson(it) }
      )
    }
}
