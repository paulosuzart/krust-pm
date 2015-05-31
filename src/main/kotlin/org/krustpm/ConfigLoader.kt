package org.krustpm

import java.io.File
import com.google.common.primitives.Ints

import  com.moandjiezana.toml.Toml

// Used to workaround toml parser
data class TomlEnvWrapper(val env : Map<String, String> = mapOf())

fun Toml.asManagedProcess(defaults : Toml) : ManagedProcess  {
  return ManagedProcess(
    name = this.getString(Main.CFG_PROCESS_NAME),
    cmd = this.getString(Main.CFG_CMD),
    maxRetries = Ints.checkedCast(this.getLong(Main.CFG_MAX_RETRIES) ?: defaults.getLong(Main.CFG_MAX_RETRIES)),
    initScale = Ints.checkedCast(this.getLong(Main.CFG_INSTANCES) ?: defaults.getLong(Main.CFG_INSTANCES)),
    env = this.to(javaClass<TomlEnvWrapper>()).env
    )
}

fun loadProcesses(toml : Toml) : List<ManagedProcess> {
  val configs = toml.getTables(Main.CFG_PROCESSES).map {
    it.asManagedProcess(toml)
  }
  if (toml.getBoolean(Main.CFG_DEBUG_CFG)) {
    println("Config debugging enabled")
    configs.forEach { println(it) }
  }
  return configs
}

fun parseConfig(cfgFile : File) : Toml {
  val defaults = Toml().parse("""
  ${Main.CFG_MAX_RETRIES} = 0
  ${Main.CFG_INSTANCES}   = 1
  ${Main.CFG_SERVER_NAME} = "localhost"
  ${Main.CFG_SERVER_PORT} = 4567
  ${Main.CFG_LOG_DIR}     = "/var/logs"
  ${Main.CFG_ENV}         = "env = {}"
  ${Main.CFG_DEBUG_CFG}   = false
  """)
  return Toml(defaults).parse(cfgFile)
}
