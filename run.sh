#!/bin/bash
java -cp planetiler.jar MyProfile.java --output data/roads.pmtiles --force 2>&1 | tee logs.txt
