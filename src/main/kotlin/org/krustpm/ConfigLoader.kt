package org.krustpm

import java.io.File
import com.google.common.primitives.Ints

import  com.moandjiezana.toml.Toml

fun Toml.asManagedProcess(defaults : Toml) : ManagedProcess  {
  return ManagedProcess(
    name = this.getString(Main.CFG_PROCESS_NAME),
    cmd = this.getString(Main.CFG_CMD),
    maxRetries = Ints.checkedCast(this.getLong(Main.CFG_MAX_RETRIES) ?: defaults.getLong(Main.CFG_MAX_RETRIES)),
    initScale = Ints.checkedCast(this.getLong(Main.CFG_INSTANCES) ?: defaults.getLong(Main.CFG_INSTANCES)),
    env = mapOf<String, String>()
    )
}

fun loadProcesses(toml : Toml) : List<ManagedProcess> {
  return toml.getTables(Main.CFG_PROCESSES).map {
    it.asManagedProcess(toml)
  }
}

fun parseConfig(cfgFile : File) : Toml {
  val defaults = Toml().parse("""
  ${Main.CFG_MAX_RETRIES} = 0
  ${Main.CFG_INSTANCES}   = 1
  ${Main.CFG_SERVER_NAME} = "localhost"
  ${Main.CFG_SERVER_PORT} = 4567
  ${Main.CFG_LOG_DIR}     = "/var/logs"
  """)
  return Toml(defaults).parse(cfgFile)
}
