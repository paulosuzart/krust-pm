package org.krustpm

import kotlin.platform.platformStatic
import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.strands.Strand
import co.paralleluniverse.strands.Strand.UncaughtExceptionHandler
import co.paralleluniverse.strands.SuspendableRunnable
import co.paralleluniverse.actors.ActorRef
import co.paralleluniverse.fibers.SuspendExecution
import co.paralleluniverse.actors.behaviors.ProxyServerActor
import co.paralleluniverse.fibers.SuspendExecution

import java.lang.Runnable

import org.zeroturnaround.exec.InvalidExitValueException
import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.StartedProcess

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

import spark.Spark.*
import spark.Request
import spark.Response

import com.google.gson.Gson


enum class ProcessStatus() {
    Started
    Running
    Done
    RetriesExceeded
}

trait ProcessManagerTrait {
  [throws(javaClass<SuspendExecution>())]
  public fun manage(process : ManagedProcessTrait)

  [throws(javaClass<SuspendExecution>())]
  public fun startAll()

  [throws(javaClass<SuspendExecution>())]
  public fun getStatus() : List<ManagedProcessJson>

  [throws(javaClass<SuspendExecution>())]
  public fun scale(name : String, to : Int) : Int

}

class ProcessManager() : ProxyServerActor("krust-pm", true),
                         ProcessManagerTrait {
  val processes = hashMapOf<String, ManagedProcessTrait>()

  [throws(javaClass<SuspendExecution>())]
  override public fun manage(process : ManagedProcessTrait) {
      this.processes[process.getName()] = process
  }

  [throws(javaClass<SuspendExecution>())]
  override public fun startAll() {
    for ((k, v) in this.processes) {
      v.scale(null)
    }
  }

  [throws(javaClass<SuspendExecution>())]
  override public fun scale(name : String, to : Int) : Int {
    return this.processes[name]?.scale(to) ?: 0
  }

  [throws(javaClass<SuspendExecution>())]
  override public fun getStatus() = this.processes.map {it.value.getStatus()}
}

public data class ManagedProcessJson(val name : String,
                                     val cmd : String,
                                     val status : ProcessStatus,
                                     val totalInstances : Int,
                                     val instances : List<ManagedProcessInstanceJson>)

public data class ManagedProcessInstanceJson(val id : Int,
                                             val currentTry : Int,
                                             val status : ProcessStatus)

public trait ManagedProcessTrait {
  [throws(javaClass<SuspendExecution>())]
  public fun scale(to : Int?): Int

  [throws(javaClass<SuspendExecution>())]
  public fun getStatus(): ManagedProcessJson

  [throws(javaClass<SuspendExecution>())]
  public fun getName(): String

}

class ManagedProcess(private val name : String,
                     private val cmd : String,
                     private val maxRetries : Int,
                     private var initScale : Int) :
                     ProxyServerActor(name, true), ManagedProcessTrait {
  var instanceCount : Int = 0
  var currentTry : Int = 0
  val instances = arrayListOf<ManagedProcess.Instance>()
  var processStatus =  ProcessStatus.Started
  val logger = LoggerFactory.getLogger(javaClass<ManagedProcess>())

  init {
    MDC.put("process", name)
  }

  [throws(javaClass<SuspendExecution>())]
  override public fun getName() = this.name

  [throws(javaClass<SuspendExecution>())]
  protected fun scaleUp(to : Int) {
    logger.debug("Scaling up to $to")
    for (i in 1..to) {
        this.instances.add(Instance(instanceCount++))
    }
  }

  [throws(javaClass<SuspendExecution>())]
  protected fun scaleDown(to : Int) {
    logger.debug("Scaling down to $to")
    this.instances.take(to).map {
      this.logger.info("Killing instance ${it.id}")
      it.kill()
      this.instances.remove(it)
    }
  }

  [throws(javaClass<SuspendExecution>())]
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


  [throws(javaClass<SuspendExecution>())]
  override public fun getStatus() : ManagedProcessJson {
    val i = this.instances.map { it.getStatus() }
    return ManagedProcessJson(
            name = this.name,
            cmd = this.cmd,
            status = this.processStatus,
            totalInstances = this.instances.size(),
            instances = i
          )
    }

    inner class Instance(val id : Int) {
      val strand : Strand
      var currentTry : Int = 0
      var processStatus =  ProcessStatus.Started
      var process : StartedProcess? = null
      val logger = LoggerFactory.getLogger(javaClass<ManagedProcess>())

      [throws(javaClass<SuspendExecution>())]
      init {
        val thread = Thread(Runnable {
          MDC.put("process", "${this@ManagedProcess.name}-$id")
          this.processStatus = ProcessStatus.Running
          while(true) {
            this.currentTry = this.currentTry + 1

            this.process = ProcessExecutor()
                  .command(this@ManagedProcess.cmd)
                  .redirectOutput(System.out)
                  .info(logger)
                  .start()

            logger.info("started")
            val result = this.process!!.getFuture().get()
            if (result.getExitValue() == 0) {
              logger.info("finished at try ${this.currentTry}")
              this.processStatus = ProcessStatus.Done
              break
            } else {
              logger.info("finished with error")
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

      [throws(javaClass<SuspendExecution>())]
      public fun getStatus() : ManagedProcessInstanceJson =
        ManagedProcessInstanceJson(this.id, this.currentTry, this.processStatus)

      [throws(javaClass<SuspendExecution>())]
      public fun kill() {
        this.strand.interrupt()
        this.process?.getProcess()?.destroy()
      }
    }
}


public class Main {

  companion object {
    platformStatic public fun main(args: Array<String>) {

      // TODO: Parse TOML
      val p1 = ManagedProcess("good_sleeper",
                              "./src/main/resources/sleeper.py",
                              3, 8).spawn() as ManagedProcessTrait
      /*val p2 = ManagedProcess("bad_sleeper",
                              "./src/main/resources/bad_sleeper.py",
                              3, 1).spawn() as ManagedProcessTrait*/

      val kpm = ProcessManager().spawn() as ProcessManagerTrait
      kpm.manage(p1)
      /*kpm.manage(p2)*/
      kpm.startAll()
      val gson = Gson()

      get("/", {req, res ->
        kpm.getStatus()
      },
      { gson.toJson(it)})

      get("/ps/scale", {req, res ->
        // TODO: Handle Exception
        val name = req.queryParams("name")
        val to = req.queryParams("to")
        kpm.scale(name, Integer.valueOf(to))
        },
        {gson.toJson(it)})
    }
  }

}
