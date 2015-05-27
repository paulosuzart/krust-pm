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

}

class ProcessManager() : ProxyServerActor("krust-pm", false),
                         ProcessManagerTrait {
  val processes = hashMapOf<String, ManagedProcessTrait>()

  [throws(javaClass<SuspendExecution>())]
  override public fun manage(process : ManagedProcessTrait) {
      this.processes[process.getName()] = process
  }

  [throws(javaClass<SuspendExecution>())]
  override public fun startAll() {
    for ((k, v) in this.processes) {
      v.startCmd()
    }
  }

  [throws(javaClass<SuspendExecution>())]
  override public fun getStatus() = this.processes.map {it.value.getStatus()}
}

public data class ManagedProcessJson(val name : String,
                                     val cmd : String,
                                     val currentTry : Long,
                                     val status : ProcessStatus)

public trait ManagedProcessTrait {
  [throws(javaClass<SuspendExecution>())]
  public fun startCmd()

  [throws(javaClass<SuspendExecution>())]
  public fun getStatus(): ManagedProcessJson

  [throws(javaClass<SuspendExecution>())]
  public fun getName(): String

}

class ManagedProcess(private val name : String,
                     private val cmd : String,
                     private val maxRetries : Long) :
                     ProxyServerActor(name, false), ManagedProcessTrait {
  var currentTry = 0L
  var processStrand : Strand? = null
  var processStatus =  ProcessStatus.Started

  [throws(javaClass<SuspendExecution>())]
  override public fun getName() = this.name

  /**
  * Starts the target process. Will keep trying for `maxRetries`.
  * If `maxRetries` is `0` will try forever.
  */
  [throws(javaClass<SuspendExecution>())]
  override public fun startCmd() {

    val thread = Thread(Runnable {

      this.processStatus = ProcessStatus.Running
      val logger = LoggerFactory.getLogger(javaClass<ManagedProcess>())
      MDC.put("process", this.name);
      while(true) {
        this.currentTry = this.currentTry + 1

        val p = ProcessExecutor()
              .command(this.cmd)
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
          if (this.currentTry == this.maxRetries) {
            logger.info("No more retries left")
            this.processStatus = ProcessStatus.RetriesExceeded
            break
          }
        }
      }

    })
    this.processStrand = Strand.of(thread)
    this.processStrand!!.start()
  }

  [throws(javaClass<SuspendExecution>())]
  override public fun getStatus() =
    ManagedProcessJson(
      this.name,
      this.cmd,
      this.currentTry,
      this.processStatus
    )
}


public class Main {

  companion object {
    platformStatic public fun main(args: Array<String>) {

      // TODO: Parse TOML
      val p1 = ManagedProcess("good_sleeper", "./src/main/resources/sleeper.py", 3).spawn() as ManagedProcessTrait
      val p2 = ManagedProcess("bad_sleeper", "./src/main/resources/bad_sleeper.py", 3).spawn() as ManagedProcessTrait

      val kpm = ProcessManager().spawn() as ProcessManagerTrait
      kpm.manage(p1)
      kpm.manage(p2)
      kpm.startAll()
      val gson = Gson()

      get("/", {req, res ->
        kpm.getStatus()
      },
      { gson.toJson(it)})
    }
  }

}
