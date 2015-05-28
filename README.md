krust-pm - Intro
=======

*Formeley `rust-pm (available at http://crates.io), now rewritten in [Kotlin](kotlinlang.org)*

This is a pocket process manager. At some point it will be a better imitation of python's systemd.

Behind the scenes it uses [Quasar actors and fibers](http://docs.paralleluniverse.co/quasar/) to handle the whole thing.

Using it is very simple, just drop a rust-pm.toml in the same folder your
`krust-pm` starts with a config like:

```toml
server-port = "localhost:4000"
[good_sleeper] # Process name
command = "./sleeper.py" # right now support 0 args commands
max_retries = 3 # 0 means forever
instances = 2
```

And with luck you open your browser at `localhost:4000` and will see something like:

```json
[
  {
    name: "good_sleeper",
    cmd: "./sleeper.py",
    status: "Started",
    totalInstances: 2,
    instances: [
      {
        id: 0,
        currentTry: 1,
        status: "Done"
      },
      {
        id: 1,
        currentTry: 1,
        status: "Done"
      }
    ]
  },
  //...
]

```

`krust-pm` start and watch the process. If it fails it starts another instance for maximum `max_retries` times.


WARN
====

This is an early stage project, Not used in production yet.


TODO
====

   - Augment the API to support scaling processes down and up. **Partially**, pending `http` API
   - Implement 0 `max_retries` to mean infinite.
   - Allow configuration of `workdir` for processes.
   - Add command line parser to specify config file.
   - Add `env` per process.
   - Specify the os `user` that must run a managed process

Licensing
===
MIT
