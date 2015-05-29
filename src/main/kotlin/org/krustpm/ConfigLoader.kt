package org.krustpm

import java.io.File
import com.google.common.primitives.Ints

import  com.moandjiezana.toml.Toml

fun Toml.asManagedProcess(defaults : Toml) : ManagedProcess  {
  return ManagedProcess(
    this.getString(Main.CFG_PROCESS_NAME),
    this.getString(Main.CFG_CMD),
    Ints.checkedCast(this.getLong(Main.CFG_MAX_RETRIES) ?: defaults.getLong(Main.CFG_MAX_RETRIES)),
    Ints.checkedCast(this.getLong(Main.CFG_INSTANCES) ?: defaults.getLong(Main.CFG_INSTANCES)))
}

fun loadProcesses(toml : Toml) : List<ManagedProcess> {
  return toml.getTables(Main.CFG_PROCESSES).map {
    it.asManagedProcess(toml)
  }
}

fun parseConfig(cfgFile : File) : Toml {
  println(cfgFile)
  val defaults = Toml().parse("""
  ${Main.CFG_MAX_RETRIES} = 0
  ${Main.CFG_INSTANCES}   = 1
  ${Main.CFG_SERVER_NAME} = "localhost"
  ${Main.CFG_SERVER_PORT} = 4567
  ${Main.CFG_LOG_DIR}     = "/var/logs"
  """)
  return Toml(defaults).parse(cfgFile)
}
