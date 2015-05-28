#! /usr/bin/python
from time import sleep
import sys

for x in range(1, 12):
  print "I'll take some rest"
  sys.stdout.flush()
  sleep(3)

print "Done sleeping"
