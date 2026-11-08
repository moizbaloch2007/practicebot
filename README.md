# PracticeArenas

Mode menu -> arena menu -> teleport to a free player slot -> player kit -> PracticeBot bot.

## Build the jar (no installs, uses GitHub)
1. Make a free account at github.com and create a new repository (any name, Public or Private).
2. Click "uploading an existing file" and drag in EVERYTHING from this folder
   (build.gradle, settings.gradle, src, .github, README.md).
   Important: the hidden ".github" folder must be uploaded too.
3. Click Commit changes.
4. Open the "Actions" tab, wait ~2 minutes for "Build plugin" to turn green.
5. Click the finished run, download the "PracticeArenas" artifact (a zip), unzip it -> PracticeArenas.jar
6. Put PracticeArenas.jar in your server's /plugins folder and restart.

If the run goes red, open it and copy the red error text to Claude.

## Set up arenas (in game, as admin)
    /parena create plains plains-1
    (stand on the first player spawn)  /parena addspawn plains-1
    (stand on the second spawn)        /parena addspawn plains-1
    ... one /parena addspawn per player slot (6 spawns = 6 players)
    /parena list

Players use /practice (or the nether star) and /leave.
