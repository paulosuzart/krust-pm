krust-pm - Intro
=======

*Formeley `rust-pm (available at http://crates.io), now rewritten in [Kotlin](kotlinlang.org)*

This is a pocket process manager. It is kinda imitation of python's `supervisord`.

Behind the scenes it uses [Quasar actors and fibers](http://docs.paralleluniverse.co/quasar/) to handle the whole thing.

Using it is very simple, just drop a `krust-pm.toml` in the same folder your
`krust-pm` starts, and add a config like:

```toml
server_name  = "localhost"
server_port  = 4000
log_dir      = "/tmp"
debug_config = true

[[processes]]
name = "good_sleeper"
cmd = "./sleeper.py" # right now support 0 args commands
max_retries = 3 # 0 means forever
instances = 8
work_dir = "./src/main/resources"
[processes.env]
  MY_ENVAR = "test.ok"
  NACHO_KEY = "27017999929231"

[[processes]] # Process name
name = "bad_sleeper"
cmd = "./src/main/resources/bad_sleeper.py" # right now support 0 args commands
max_retries = 3 # 0 means forever
```

And with luck you open your browser at `localhost:4000` and will see something like:

```javascript
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
  //...
]

```

`krust-pm` start and watch the process. If it fails it starts another instance for maximum `max_retries` times.

REST API plus additional info
---

Resource  | Verb | Path | Returns
--------- | ---- | ---- | --------
`root`    | GET  | `/`    | Json with Process details (see above). `?status=S` will match `S` to at least one instance with the given status.
`process` | GET  | `/ps` | Context for interacting with processes. See below.
`process`  | GET | `/ps/scale` | Returns the number of afected instances. `name=NAME` is the target process, `to=AMOUNT` sets the number of instances of `NAME` to `AMOUNT`. You can't specify which instance will be killed.

Additional Info:
   - `krust-pm` is designed to manage long running processes such as `python` process, web servers etc. But in case of any process instance finishes, it will be with status Done. All managing resources (not Java Process Management) will be hanging on the server.
   - If you scale down any process, it will remove the first `AMOUNT` of elements of the internal list, regardless of their status.
   - There is no way to get the `PID` of an instance.
   - There is no way to specify the user that runs a command


WARN
====

This is an early stage project, Not used in production yet.

But if you want to try it:

   1. clone the repo
   2. run `gradle mavenCapsule`
   3. fire it: java -javaagent:$PATH_TO_QUASAR_JAR -jar build/libs/krust-pm-capsule.jar


TODO
====

   - Augment the API to support scaling processes down and up. **Done**
   - Implement 0 `max_retries` to mean infinite. **Done**
   - Allow configuration of `workdir` for processes. **Done**
   - Add command line parser to specify config file. **Done**
   - Add `env` per process. **Done**


Licensing
===
MIT
