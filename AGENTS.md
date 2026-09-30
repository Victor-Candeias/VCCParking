# Maestru — Agent Guide

This project uses **Maestru** for spec-driven development. `.maestru/` is the source of truth for all project knowledge — specs, work-items, docs, and templates.

## 1. Mandatory Workflow — For ALL Tasks

**This applies to every task: coding, codebase exploration, debugging, answering questions about the project, or investigating whether a feature exists.** There are no exceptions — always start with Maestru.

### Before any work

1. **`maestru search <query>`** — find relevant context: specs, docs, prior work, terminology
2. **`maestru sql "<query>"`** — find structured information: work-items, statuses, ownership, templates, dependencies

Do this before reading files, writing code, or making implementation decisions. Always prefer `maestru search` and `maestru sql` over grep/glob or exploratory file reads for initial context discovery.

### After any `.maestru/` change

3. **`maestru check`** — mandatory validation gate. A task is not complete until `maestru check` passes.

### Do not

- Start by reading random `.maestru/` files
- Use grep/glob before querying Maestru
- Assume work-item, spec, or template state without querying it
- Consider a change valid without `maestru check`

## 2. Source of Truth

Markdown/frontmatter in `.maestru/` is the source of truth. `maestru sql` is a derived interface over the indexed state. `maestru check` is the required validation gate. SQL write operations (UPDATE, INSERT) compile into validated markdown edits — they never mutate SQLite as authoritative state.

## 3. Commands

| Command | Purpose |
|---------|---------|
| `maestru search <query>` | Natural language search across all documents |
| `maestru sql "<query>"` | SQL queries, updates, and inserts against the entity index |
| `maestru check` | Validate all `.maestru/` files, rebuild indexes |
| `maestru help [cmd]` | Usage details |

### `maestru search`

```
maestru search authentication --type doc
maestru search "business model"
maestru search api --exclude-tag draft
```

Use `--type <doc|work-item|work-spec|work-track>` to narrow results. Only fall back to grep/glob when `maestru search` does not return what you need — e.g. pattern-matching inside source code.

### `maestru sql`

**Per-type tables:**
- `work_tracks` (id, path, title, description, status, owner, created)
- `work_items`  (id, path, title, **track_id**, status, owner, priority, created, completed, branch, pr)
- `work_specs`  (id, path, title, **work_item_id**, template, owner, created)
- `docs`        (id, path, title, description, tags, owner, created, updated)

**Relationship tables:**
- `work_item_specs`  (work_item_id, work_spec_id)
- `work_item_blocks` (blocker_id, blocked_id)
- `refs`             (source_id, target_path)
- `templates`        (name, type, mode, description)

**SELECT — lookup and filtering:**
```
maestru sql "SELECT id, status FROM work_items WHERE status='in-progress' ORDER BY id"
maestru sql "SELECT * FROM work_items WHERE id='L63'"
maestru sql "SELECT name, mode FROM templates WHERE type='work-spec'"
```

**JOIN — across per-type tables via FK columns:**
```
maestru sql "SELECT s.title, i.status FROM work_specs s JOIN work_items i ON s.work_item_id=i.id WHERE i.status='in-progress'"
```

**UPDATE — modify frontmatter, syncs back to markdown:**
```
maestru sql "UPDATE work_items SET status='done' WHERE id='L63'"
```

Auto-sets `completed` on terminal status transitions. Use `--dry-run` to preview changes. `id` and `path` are immutable. FK targets are prevalidated — a missing target errors with `reference "<col>" → <id> not found` before any markdown is touched.

**INSERT — create documents, template-aware:**
```
maestru sql "INSERT INTO work_items (id, title, track_id, status, priority) VALUES ('L70', 'New feature', 'track-l', 'backlog', 'medium')"
maestru sql "INSERT INTO work_specs (id, title, work_item_id, template, owner) VALUES ('l70-spec', 'L70 Spec', 'L70', 'implementation-plan-v1', 'filipe')"
```

When a template is specified or enforced, the body is rendered with placeholder substitution. Discover templates with `SELECT * FROM templates`.

**INSERT into join tables — link without editing the parent file by hand:**
```
maestru sql "INSERT INTO work_item_specs (work_item_id, work_spec_id) VALUES ('L70', 'l70-spec')"
maestru sql "INSERT INTO work_item_blocks (blocker_id, blocked_id) VALUES ('L69', 'L70')"
```

**DELETE — join tables only:**
```
maestru sql "DELETE FROM work_item_specs WHERE work_item_id='L70' AND work_spec_id='l70-spec'"
```

DELETE on per-type tables is rejected — delete the markdown file directly instead.

## 4. Schema

Common fields: `id` (required), `title` (required), `created` (required), `owner`.

| Type | Extra Fields | Statuses |
|------|-------------|----------|
| **work-track** | `description` | draft → active → on-hold → **archived** |
| **work-item** | `priority` (critical/high/medium/low), `completed`, `template`, `blocked-by`, `specs` | backlog → assigned → in-progress → **done** · **archived** |
| **work-spec** | `work-item` (required), `template` | — |
| **doc** | `description` (required), `updated` (required), `tags`, `template` | — |

Bold = terminal. UPDATE auto-sets `completed` on terminal status.

**Config reference:** `.maestru/config.yaml` declares template rules per type. Query with `maestru sql "SELECT * FROM templates"` to discover valid templates. When in doubt about valid fields, statuses, or templates, use `maestru sql` or check `.maestru/config.yaml` and `.maestru/schema.yaml`.

## 5. Spec Templates

Work-specs are implementation plans for complex work (3+ files). Each spec uses a template that defines its structure.

Discover templates:
```
maestru sql "SELECT name, mode FROM templates WHERE type='work-spec'"
```

Create a spec with a template:
```
maestru sql "INSERT INTO work_specs (id, title, work_item_id, template, owner) VALUES ('l70-spec', 'L70 Spec', 'L70', 'implementation-plan-v1', 'developer')"
```

Query `templates` via SQL first — that tells you which templates are available, their mode, and their description. Only read the template file under `.maestru/templates/` when you need the body structure itself.

| Template | Use when |
|----------|----------|
| `diagrams-v1` | Architecture or flow diagrams |
| `implementation-plan-v1` | Building a feature (phases, steps, action tables) |
| `research-v1` | Answering a question or evaluating options |
| `testing-plan-v1` | Test strategy for a feature |
| `wireframes-v1` | UI layout mockups |

## 6. Work Loop

### Before code

1. `maestru search <topic>` — gather context
2. `maestru sql "SELECT ... FROM work_items WHERE ..."` — find or verify the work-item
3. `maestru sql "UPDATE work_items SET status='in-progress' WHERE id='<ID>'"` — claim it
4. Check for a spec (required if 3+ files):
   `maestru sql "SELECT id, title FROM work_specs WHERE work_item_id='<ID>'"`

### While coding

- Treat `.maestru/` markdown as the source of truth
- Use `maestru search` and `maestru sql` over manual file hunting
- If implementation reveals a better approach, update the spec

### After code

1. Update spec/docs if needed
2. `maestru sql "UPDATE work_items SET status='done' WHERE id='<ID>'"` — mark complete
3. `maestru check` — mandatory. Fix any failures before considering the task done.

## 7. Git

- **Branches:** `{item-id}-{short-name}` (e.g., `l49-search-code-refs`)
- **Commits:** small, atomic, reference the work-item ID
- **Keep `main` stable** — only merge reviewed, working code
