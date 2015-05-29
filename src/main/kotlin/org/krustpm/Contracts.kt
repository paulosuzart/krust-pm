package org.krustpm

import co.paralleluniverse.fibers.SuspendExecution


public trait ManagedProcessTrait {
  [throws(javaClass<SuspendExecution>())]
  public fun scale(to : Int?): Int

  [throws(javaClass<SuspendExecution>())]
  public fun getStatus(): ManagedProcessJson

  [throws(javaClass<SuspendExecution>())]
  public fun getName(): String

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
