# PracticeCore - where every file goes

PracticeCore/                         <- the folder you upload to GitHub
  build.gradle                        <- ROOT folder (NOT inside src)
  settings.gradle
  README.md
  src/main/resources/plugin.yml       <- resources folder
  src/main/resources/config.yml
  src/main/java/com/practiceserver/arenas/
      Arena.java ArenaManager.java ArenasPlugin.java BotCleaner.java
      CommandHandler.java KitEditor.java KitManager.java LayoutManager.java
      MenuManager.java RegenManager.java SessionListener.java SessionManager.java
      SpawnPoint.java Text.java        <- only .java files in this folder

Your .github/workflows/main.yml on GitHub stays as it is.
