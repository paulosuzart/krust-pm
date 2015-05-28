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
    "name": "good_sleeper",
    "cmd": "./sleeper.py",
    "status": "Started",
    "totalInstances": 2,
    "instances": [
      {
        "id": 0,
        "currentTry": 1,
        "status": "Done"
      },
      {
        "id": 1, // inc forever per new instance scaled
        "currentTry": 1,
        "status": "Done" // or Done | Started | Running | RetriesExceeded
      }
    ]
  },
]

```

`krust-pm` start and watch the process. If it fails it starts another instance for maximum `max_retries` times.

REST API plus additional info
---

Resource  | Verb | Path | Returns
--------- | ---- | ---- | --------
`root`    | GET  | `/`    | Json with Process details (see above). `?status=S` will match `S` to at least one instance with the given status.
`process` | GET  | `/ps` | Returns the number of afected instances. `name=NAME` is the target process, `to=AMOUNT` sets the number of instances of `NAME` to `AMOUNT`. You can't specify which instance will be killed.

Notice:
   - `krust-pm` is designed to manage long running processes such as `python` process, web servers etc. But in case of any process instance finishes, it will be with status Done. All managing resources (not Java Process Management) will be hanging on the server.
   - If you scale down any process, it will remove the first `AMOUNT` of elements of the internal list, regardless of their status.


WARN
====

This is an early stage project, Not used in production yet.


TODO
====

   - Augment the API to support scaling processes down and up. **Done**
   - Implement 0 `max_retries` to mean infinite.
   - Allow configuration of `workdir` for processes.
   - Add command line parser to specify config file.
   - Add `env` per process.
   - Specify the os `user` that must run a managed process

Licensing
===
MIT
