---
name: codegraph
description: Use when exploring codebase structure, finding symbol definitions/callers/callees, analyzing code impact, or building context for tasks. Replaces MCP with direct CLI calls.
---

# CodeGraph CLI

## Overview

CodeGraph is a code intelligence tool that builds a knowledge graph of your codebase. Use the `codegraph` CLI directly via Bash instead of MCP — it's faster and more reliable.

## Installation

If `codegraph` is not installed, install it first:

```bash
# macOS / Linux (no Node.js required)
curl -fsSL https://raw.githubusercontent.com/colbymchenry/codegraph/main/install.sh | sh

# Windows (PowerShell)
irm https://raw.githubusercontent.com/colbymchenry/codegraph/main/install.ps1 | iex

# Or with npm (requires Node.js)
npm i -g @colbymchenry/codegraph
```

After installation, open a new terminal and initialize the project:

```bash
codegraph init -i    # Initialize and build initial index
```

GitHub: https://github.com/colbymchenry/codegraph

## Prerequisites

```bash
codegraph init -i    # Initialize and index (run once per project)
codegraph sync       # Sync after code changes
codegraph status     # Check index status
```

## Core Commands

### Search Symbols

```bash
codegraph query <search>                 # Search for symbols
codegraph query <search> -k function     # Filter by kind (function, class, method, etc.)
codegraph query <search> -l 20           # Limit results
codegraph query <search> -j              # JSON output
```

### Find Callers / Callees

```bash
codegraph callers <symbol>               # Who calls this symbol?
codegraph callees <symbol>               # What does this symbol call?
codegraph callers <symbol> -l 30         # More results
```

### Impact Analysis

```bash
codegraph impact <symbol>                # What's affected by changing this symbol?
codegraph impact <symbol> -d 3           # Deeper traversal
```

### Find Affected Tests

```bash
codegraph affected <file1> <file2>       # Which tests are affected by these changes?
codegraph affected src/foo.ts -f "*.test.ts"  # Custom test glob
git diff --name-only | codegraph affected --stdin  # From git diff
```

### Build Context for Tasks

```bash
codegraph context "implement auth flow"  # Build markdown context for a task
codegraph context "fix login bug" -n 30  # Fewer nodes
codegraph context "refactor DB" --no-code  # Structure only, no code blocks
```

### File Structure

```bash
codegraph files                          # Show project file tree
codegraph files --filter src             # Filter to src directory
codegraph files --pattern "*.ts"         # Filter by glob
codegraph files --format flat            # Flat list instead of tree
```

## Workflow

1. **Before exploring**: `codegraph sync` to ensure index is current
2. **Find symbol**: `codegraph query <name>`
3. **Trace usage**: `codegraph callers/callees <symbol>`
4. **Check impact**: `codegraph impact <symbol>`
5. **Build context**: `codegraph context <task description>`

## When to Use Each Command

| Task | Command |
|------|---------|
| Find where a function is defined | `query` |
| Find all places a function is used | `callers` |
| Understand what a function depends on | `callees` |
| Assess blast radius of a change | `impact` |
| Find tests to run after changes | `affected` |
| Get overview of codebase structure | `files` |
| Gather context for a complex task | `context` |

## Tips

- Run `codegraph sync` after pulling code or switching branches
- Use `-j` flag for JSON output when you need to process results programmatically
- `context` command is great for giving subagents a focused view of relevant code
- Combine `affected` with `git diff` to find exactly which tests to run
