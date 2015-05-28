package org.krustpm

import kotlin.platform.platformStatic
import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.strands.Strand
import co.paralleluniverse.strands.SuspendableRunnable
import co.paralleluniverse.actors.ActorRef
import co.paralleluniverse.actors.LocalActor
import co.paralleluniverse.fibers.SuspendExecution
import co.paralleluniverse.actors.behaviors.ProxyServerActor
import co.paralleluniverse.actors.behaviors.Server
import co.paralleluniverse.fibers.SuspendExecution
import java.lang.ProcessBuilder
import java.lang.Runnable
import org.zeroturnaround.exec.InvalidExitValueException
import org.zeroturnaround.exec.ProcessExecutor
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
      this.scaleUp(_to)
    } else if (this.instances.size() > _to) {
      scaled = this.instances.size() - _to
      this.scaleDown(_to)
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

      [throws(javaClass<SuspendExecution>())]
      init {
        val thread = Thread(Runnable {

          this.processStatus = ProcessStatus.Running
          val logger = LoggerFactory.getLogger(javaClass<ManagedProcess>())
          MDC.put("process", "${this@ManagedProcess.name}-$id")
          while(true) {
            this.currentTry = this.currentTry + 1

            val p = ProcessExecutor()
                  .command(this@ManagedProcess.cmd)
                  .redirectOutput(System.out)
                  .info(logger)
                  .start()

            logger.info("started")
            val result = p.getFuture().get()
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
        this.strand.start()
      }

      [throws(javaClass<SuspendExecution>())]
      public fun getStatus() : ManagedProcessInstanceJson =
        ManagedProcessInstanceJson(this.id, this.currentTry, this.processStatus)


    }
}


public class Main {

  companion object {
    platformStatic public fun main(args: Array<String>) {

      // TODO: Parse TOML
      val p1 = ManagedProcess("good_sleeper",
                              "./src/main/resources/sleeper.py",
                              3, 2).spawn() as ManagedProcessTrait
      val p2 = ManagedProcess("bad_sleeper",
                              "./src/main/resources/bad_sleeper.py",
                              3, 1).spawn() as ManagedProcessTrait

      val kpm = ProcessManager().spawn() as ProcessManagerTrait
      kpm.manage(p1)
      kpm.manage(p2)
      kpm.startAll()
      val gson = Gson()

      get("/", {req, res ->
        kpm.getStatus()
      },
      { gson.toJson(it)})

      get("/:process/scale", {req, res ->
        // TODO: Handle Exception
        val process = req.params(":process")
        val to = req.queryParams("to")
        kpm.scale(process, Integer.valueOf(to))
        },
        {gson.toJson(it)})
    }
  }

}
