# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands
- Build: `mvn clean install`
- Run: `mvn exec:java -Dexec.mainClass="io.example.MyServiceSetup"`
- Run test: `mvn test`
- Run single test: `mvn test -Dtest=GridCellEntityTest#testUpdateStatus`

## Code Style Guidelines
- Use Java 17+ features including records, switch expressions, and var
- Follow Akka SDK patterns for EventSourcedEntity and View components
- Imports: Organize static imports first, followed by standard imports
- Use clear naming for Commands/Events (UpdateStatus, StatusUpdated)
- Error handling: Return effects().error() with formatted messages
- Logging: Use SLF4J with proper log levels
- Domain model: Keep domain logic in the domain objects (GridCell)
- Method chaining: Use fluent API style where appropriate
- Test approach: Use EventSourcedTestKit for entity tests