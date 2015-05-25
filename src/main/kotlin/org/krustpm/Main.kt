package org.krustpm

import kotlin.platform.platformStatic
import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.strands.Strand
import co.paralleluniverse.strands.SuspendableRunnable
import co.paralleluniverse.strands.channels.Channel
import co.paralleluniverse.strands.channels.Channels
import co.paralleluniverse.actors.ActorRef
import co.paralleluniverse.actors.ActorRegistry
import co.paralleluniverse.actors.BasicActor
import co.paralleluniverse.actors.LocalActor
import co.paralleluniverse.fibers.SuspendExecution
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.Future
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
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


class ProcessManager() {
  val processes = ConcurrentHashMap<String, ManagedProcess>()

  public fun manage(process : ManagedProcess) {
      this.processes.put(process.name, process)
  }

  public fun startAll() {
    for (p in this.processes.values()) {
      p.start()
    }
  }
}

public data class ManagedProcessJson(val name : String, val cmd : String, val currentTry : Long, val status : ProcessStatus)


class ManagedProcess(val name : String, val cmd : String, val maxRetries : Long) {
  val currentTry = AtomicLong()
  var thread : Thread? = null
  var status =  ProcessStatus.Started

  /**
  * Starts the target process. Will keep trying for `maxRetries`.
  * If `maxRetries` is `0` will try forever.
  */
  public fun start() {

    this.thread = Thread (Runnable {

      this.status = ProcessStatus.Running
      val logger = LoggerFactory.getLogger(javaClass<ManagedProcess>())
      MDC.put("process", this.name);
      while(true) {
        currentTry.incrementAndGet()

        val p = ProcessExecutor().command(this.cmd).redirectOutput(System.out).info(logger).start()
        logger.info("started")
        val result = p.getFuture().get()
        if (result.getExitValue() == 0) {
          logger.info("finished at try ${this.currentTry.get()}")
          this.status = ProcessStatus.Done
          break
        } else {
          logger.info("finished with error")
          if (this.currentTry.get() == this.maxRetries) {
            logger.info("No more retries left")
            this.status = ProcessStatus.RetriesExceeded
            break
          }
        }
      }

    })
    this.thread!!.start()
  }

}


public class Main {

  companion object {
    platformStatic public fun main(args: Array<String>) {
      val p1 = ManagedProcess("good_sleeper", "./src/main/resources/sleeper.py", 3)
      val p2 = ManagedProcess("bad_sleeper", "./src/main/resources/bad_sleeper.py", 3)

      val kpm = ProcessManager()
      kpm.manage(p1)
      kpm.manage(p2)
      kpm.startAll()
      val gson = Gson()

      get("/", {req, res ->
        val ps = java.util.LinkedList<ManagedProcessJson>()
        for (p in kpm.processes.values()) {
          ps.add(ManagedProcessJson(p.name, p.cmd, p.currentTry.get(), p.status))
        }
        ps
      },
      { gson.toJson(it)})
    }
  }

}
