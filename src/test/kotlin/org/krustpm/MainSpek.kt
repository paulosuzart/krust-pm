package org.krustpm

import kotlin.test.assertEquals
import org.jetbrains.spek.api.*
import kotlin.test.assertTrue


class MainSpek : Spek() {init {

    given("A Managed Process") {
      val p1 = ManagedProcess("good_sleeper",
                              "./src/main/resources/sleeper.py",
                              3,
                              2,
                              mapOf<String,String>()).spawn() as ManagedProcessTrait

      on("scaling") {
        it ("should have 2 instance running after scale") {
          val scaled = p1.scale(null)
          val statusAfterScale = p1.getStatus()
          shouldEqual(2, statusAfterScale.totalInstances)
          shouldEqual(2, scaled)
        }

        it ("should scale down to 1 if request less than 1") {
          val scaled = p1.scale(0)
          val statusAfterScaleDown = p1.getStatus()
          shouldEqual(1, statusAfterScaleDown.totalInstances)
          shouldEqual(1, scaled)
        }
      }
      on("status") {
        val status = p1.getStatus()
        it ("should have the right name") {
          shouldEqual("good_sleeper", status.name)
        }

      }


    }
}}
