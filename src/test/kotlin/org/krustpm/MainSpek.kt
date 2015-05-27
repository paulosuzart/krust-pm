package org.krustpm

import kotlin.test.assertEquals
import org.jetbrains.spek.api.Spek
import kotlin.test.assertTrue

class MainSpek : Spek() {init {

    given("A Managed Process") {
      val p1 = ManagedProcess("good_sleeper", "./src/main/resources/sleeper.py", 3).spawn() as ManagedProcessTrait

      on("invoking its status") {
        val status = p1.getStatus()
        it ("should have the right name") {
          assertEquals("good_sleeper", status.name)
        }
      }
    }
}}
