krust-pm - Intro
=======

*Formeley `rust-pm (available at http://crates.io), now rewritten in [Kotlin](kotlinlang.org)*

This is a pocket process manager. At some point it will be a better imitation of systemd

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
  "name": "bad_sleeper",
  "cmd": "./src/main/resources/bad_sleeper.py",
  "currentTry": 3,
  "status": "RetriesExceeded"
}]
```

`krust-pm` start and watch the process. If it fails it starts another instance for maximum `max_retries` times.


WARN
====

This is an early stage project, Not used in production yet.


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
