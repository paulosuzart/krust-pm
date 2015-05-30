package org.krustpm

import co.paralleluniverse.fibers.SuspendExecution


interface ManagedProcessTrait {

  throws(SuspendExecution::class)
  public fun scale(to : Int?): Int

  throws(SuspendExecution::class)
  public fun getStatus(): ManagedProcessJson

  throws(SuspendExecution::class)
  public fun getName(): String

}

interface ProcessManagerTrait {

  throws(SuspendExecution::class)
  public fun manage(process : ManagedProcessTrait)

  throws(SuspendExecution::class)
  public fun startAll()

  throws(SuspendExecution::class)
  public fun getStatus() : List<ManagedProcessJson>

  throws(SuspendExecution::class)
  public fun scale(name : String, to : Int) : Int

}
