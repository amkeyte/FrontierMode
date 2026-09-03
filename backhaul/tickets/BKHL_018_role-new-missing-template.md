---
id: BKHL_018
uid: BKHL
number: 18
client: Backhaul
status: open
title: 'bhrole new: missing role.md.tmpl in package'
context: 'bhrole new crashes: FileNotFoundError, roles/templates/role.md.tmpl not
  shipped in the installed package.'
priority: normal
opened: '2026-09-03'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

`bhrole new` (creating a fresh role page) crashes outright:

```
FileNotFoundError: [Errno 2] No such file or directory:
'.../site-packages/backhaul/modules/roles/templates/role.md.tmpl'
```

`roles/templates/` doesn't exist at all in this pip install (`find` across the installed
package turned up nothing named `role.md.tmpl` anywhere) -- looks like a packaging gap
(`MANIFEST.in`/`package_data` not including the roles module's template alongside its code),
not a logic bug in `create.py`/`templating.py` themselves. `bhrole index`/`refresh` (which
operate on existing role files rather than templating a new one) are unaffected -- confirmed
by hand-writing a new role page directly and running `bhrole refresh` successfully afterward.

**Workaround used this session:** wrote the new role's `.md` file by hand, matching the existing
role pages' exact structure, then ran `bhrole refresh`/`index` to register it -- works fine,
just skips the templated scaffold `bhrole new` is supposed to provide.

## Log

- 2026-09-03: Ticket opened, found while creating the Cartographer role.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/backhaul)
<!-- bh-header:end -->
