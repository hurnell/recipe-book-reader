

```shell

```

```shell
adb shell chmod 777 /data/local/tmp
adb shell mkdir /data/local/tmp/tools
adb shell chmod 777 /data/local/tmp/tools
adb push tools/sqlite3 /data/local/tmp/tools/sqlite3

adb shell
run-as apk.hurnell.recipebookreader

cp /data/local/tmp/tools/sqlite3 ./files/sqlite3

```

```shell
adb shell ime reset

adb shell #then
run-as apk.hurnell.recipebookreader

./files/sqlite3 ./databases/recipe-reader.db
```


## Updating PDF metadata locally (before uploading to phone - note if you change the metadata best to also change the name of the file on the phone as well)
```shell

brew install exiftool

exiftool -Keywords file.pdf # get current metadata keywords
exiftool file.pdf # get all current metadata
exiftool -Keywords="main_category=Cookbooks, sub1_category=Sweets , sub2_category=Desserts" file.pdf # update metadata keywords
```
Note: You can install and use exiftool on your phone but only through Termux (which is beyond the scope of this tutorial).

Saved. The plan is at /Users/nigel/.claude/plans/inherited-crunching-wolf.md