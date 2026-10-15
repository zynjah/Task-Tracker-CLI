# Task Tracker CLI

A command-line task tracker written in Java. It uses only the JDK and stores
tasks in `tasks.json` in the current working directory.

## Requirements

- Java JDK installed (the `javac` and `java` commands must be available).

## Compile and run

Open PowerShell in this project folder (`Task Tracker`) and compile the program:

```powershell
javac TaskCli.java
```

Run a command with `java TaskCli`:

```powershell
java TaskCli add "Buy groceries"
java TaskCli list
```

The first command creates `tasks.json` in the current directory if it does not
already exist. Keep running the commands from the same folder to use the same
task list.

## Commands

```text
java TaskCli add <description>
java TaskCli update <id> <description>
java TaskCli delete <id>
java TaskCli mark-in-progress <id>
java TaskCli mark-done <id>
java TaskCli list
java TaskCli list todo
java TaskCli list in-progress
java TaskCli list done
```

Put descriptions containing spaces in quotes. The `add` command prints the new
task's ID; use that ID for updating, deleting, and changing its status.

## Task data

Each task in `tasks.json` has an `id`, `description`, `status`, `createdAt`, and
`updatedAt`. Status is one of `todo`, `in-progress`, or `done`. Updating a task
or changing its status refreshes `updatedAt`; `createdAt` stays unchanged.

## Project by:
https://roadmap.sh/projects/task-tracker
