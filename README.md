krust-pm - Intro
=======

*Formeley `rust-pm (available at http://crates.io), now rewritten in [Kotlin](kotlinlang.org)*

This is a pocket process manager. At some point it will be a better imitation of systemd
Take a look at this [![asciicast](https://asciinema.org/a/20286.png)](https://asciinema.org/a/20286).

Using it is very simple, just drop a rust-pm.toml in the same folder your
`krust-pm` starts with a config like:

```toml
server-port = "localhost:4000"
[bad_sleeper] # Process name
command = "./bad_sleeper.py" # right now support 0 args commands
max_retries = 3 # 0 means forever
```

And with luck you open your browser at `localhost:4000` and will see something like:

```json
[{
  "command" : "./bad_sleeper.py",
  "max_retries" : 0,
  "name" : "bad_sleeper",
  "status" : {
    "meta" : 1,
    "state" : "running"
  }
}]
```

`rust-pm` start and watch the process. If it fails it starts another instance for maximum `max_retries` times.

Ideas
=====

By now the `stdin`, `stdout` and `stder` are piped. We may change it to inherited, so the `rust-pm` output is in fact the process output. It depends on what we will do for logging.


WARN
====

This is a early stage project, Not used in production yet.


TODO
====

   - Augment the API to support scaling processes down and up.
   - Implement 0 `max_retries` to mean infinite.
   - Allow configuration of `workdir` for processes.
   - Add command line parser to specify config file.
   - Add `env` per process.
   - Specify the user that must run a managed process

Licensing
===
MIT
