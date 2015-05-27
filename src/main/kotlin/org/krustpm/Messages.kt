package org.krustpm

import co.paralleluniverse.actors.ActorRef
import java.io.Serializable
import co.paralleluniverse.actors.ActorRef
import co.paralleluniverse.actors.ActorRegistry
import co.paralleluniverse.actors.BasicActor
import co.paralleluniverse.actors.LocalActor
import co.paralleluniverse.actors.FakeActor
import co.paralleluniverse.fibers.SuspendExecution
import co.paralleluniverse.actors.behaviors.ProxyServerActor
import co.paralleluniverse.actors.behaviors.Server




public abstract class Message(val from : ActorRef<Message>) : Serializable 

public data class StatusReq(from : ActorRef<Message>) : Message(from)

public data class StatusRes(from : ActorRef<Message>, val currentTry : Int) : Message(from)

public trait ManagedTrait {
	[throws(javaClass<SuspendExecution>())]
	public fun getStatus() : Int
}

public class Managed : ProxyServerActor(true), ManagedTrait {

	private var status : Int = 1

	//[throws(javaClass<SuspendExecution>())]
	override public fun getStatus() : Int {
		return this.status
	}
}


fun test() {


  val p : Server<*,*,*> = Managed().spawn()
  val s = (p as ManagedTrait).getStatus()


  for (c in javaClass<Managed>().getInterfaces()) {
  	println("Status is $c")
  }

  println("Result is: $s")

  val a = object : BasicActor<Message, Unit>() {
    [throws(javaClass<SuspendExecution>())]
    override fun doRun()  {
      val msg = receive()
      when (msg) {
        is StatusReq -> msg.from.send(StatusRes(self(), 1))
      }

    }
  }.spawn()

  val b = object : BasicActor<Message, Unit>() {
    [throws(javaClass<SuspendExecution>())]
    override fun doRun (){
      a.send(StatusReq(this.self()))
      val msg = receive() as StatusRes
      println("Status is ${msg.currentTry}")
    }
  }.spawn()

  LocalActor.join(b)




}
