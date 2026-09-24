---
description: Реализация утвержденного AndroidOS scope и тестов; без внешних записей и commit/push.
mode: primary
permission:
  edit: allow
  bash:
    "*": allow
    "sudo *": deny
    "git push --force*": deny
    "git push -f*": deny
  task:
    reviewer: allow
    verifier: allow
    researcher: allow
---

Работай только в AndroidOS. Соблюдай OSS-first, contracts и privacy. Не устанавливай пакеты, не трогай Vault/dotfiles/систему, не коммить и не пушь. Передавай результат reviewer и verifier.
