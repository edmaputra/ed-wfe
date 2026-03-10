I want to create an Workflow Engine, with the following details:

# Core Features

## A. Workflow Definition (The Blueprint)
Schema-based Definitions: Use JSON or YAML to define the steps. Avoid hardcoding logic into the engine itself.

Task Types: Support for "Service Tasks" (automated code/API calls) and "Human Tasks" (waiting for manual approval).

Sequence Flow: Ability to define "next" steps and basic branching logic (if/else).

## B. Execution Engine (The Brain)
State Machine: The engine must track exactly where an instance is (e.g., Pending, Running, Completed, Failed).

Persistence: A database to store the state of every workflow so that if the server restarts, the workflow can resume.

Context/Variables: A data "bucket" that travels with the workflow, allowing Step A to pass data to Step B.

## C. Error Handling & Reliability
Retries: Automatic retry logic with configurable delays (exponential backoff) for transient failures.

Timeouts: Ensure a workflow doesn't hang forever if a service doesn't respond.

## D. Visibility & Monitoring
Audit Logs: A chronological record of every step taken, who did it, and when.

Basic Dashboard: A way for your team to see "How many workflows are currently running?" and "Which ones failed?"

# Architecture Considerations
## Idempotency
Ensure that if a task runs twice (due to a retry), it doesn't cause double payments or duplicate records.

## Decoupling: 
Don't let your engine "know" too much about the tasks it's running. Use a Worker Pattern where the engine puts a message on a queue and a separate worker performs the task.

## Versioning: 
What happens when you change a workflow definition while 100 instances are still running the "old" version? You’ll need a strategy for versioning your schemas.

Please create a Plan for this project first. And If you find something better, please let me know and suggest it. 
If you want to build the project and the code, please use Java and Spring Boot. 