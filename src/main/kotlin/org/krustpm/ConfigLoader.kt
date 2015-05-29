package org.krustpm

import java.io.File
import com.google.common.primitives.Ints

import  com.moandjiezana.toml.Toml

fun Toml.asManagedProcess(defaults : Toml) : ManagedProcess  {
  return ManagedProcess(
    this.getString("name"),
    this.getString("cmd"),
    Ints.checkedCast(this.getLong("max_retries") ?: defaults.getLong("max_retries")),
    Ints.checkedCast(this.getLong("instances") ?: defaults.getLong("instances")))
}

fun loadProcesses(toml : Toml) : List<ManagedProcess> {
  return toml.getTables("processes").map {
    it.asManagedProcess(toml)
  }
}

fun parseConfig(cfgFile : File) : Toml {
  println(cfgFile)
  val defaults = Toml().parse("""
  max_retries = 0
  instances = 1
  server_name = "localhost"
  server_port = 4567
  log_dir     = "/var/logs"
  """)
  return Toml(defaults).parse(cfgFile)
}
