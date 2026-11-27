#!/usr/bin/env python3
"""
Verificação real da IA do NOVA (Claude). Rode com o backend no ar e ANTHROPIC_API_KEY definida no ambiente dele:

    python3 scripts/testar_ia.py              # usa http://localhost:8080
    python3 scripts/testar_ia.py http://host:8080 caminho/da/imagem.png

Cria uma conta de teste, usa o Copilot (proposta e ajuda por campo), lê um esboço (se passar uma imagem), gera o projeto,
pergunta ao projeto e pede a prévia de uma melhoria com IA. Mostra PASS ou FAIL em cada etapa. Só usa a biblioteca padrão.
"""
import json
import mimetypes
import sys
import urllib.error
import urllib.request
import uuid

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/") + "/api/v1/"
IMAGE = sys.argv[2] if len(sys.argv) > 2 else None
results = []


def call(method, path, body=None, token=None, raw_body=None, content_type="application/json"):
    data = raw_body if raw_body is not None else (None if body is None else json.dumps(body).encode())
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", content_type)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            text = r.read().decode()
            return r.status, json.loads(text) if text else None
    except urllib.error.HTTPError as e:
        text = e.read().decode()
        try:
            return e.code, json.loads(text)
        except ValueError:
            return e.code, {"detail": text[:300]}


def check(name, ok, extra=""):
    results.append(("PASS" if ok else "FAIL", name, extra))
    print(("PASS " if ok else "FAIL ") + name + (" — " + extra if extra else ""), flush=True)


u = uuid.uuid4().hex[:8]
status, body = call("POST", "auth/register", {"name": "Teste IA", "username": "ia_" + u, "email": f"ia{u}@exemplo.test",
                                              "password": "Senha1234", "birthDate": "1990-01-01", "acceptTerms": True})
if status != 201:
    sys.exit(f"Não consegui criar a conta de teste ({status}): {body}")
token = body["accessToken"]

status, st = call("GET", "copilot/status", token=token)
check("IA configurada no backend", st and st.get("provider") == "claude", f"provider={st and st.get('provider')} modelo={st and st.get('model')}")

status, prop = call("POST", "copilot/propose", {"idea": "Uma agenda online para uma clínica de fisioterapia, com cadastro de pacientes, "
                                                        "consultas com status e login para a equipe"}, token)
check("Copilot propõe pela IA", status == 200 and prop.get("source") == "claude", (prop or {}).get("notice") or "")
spec = prop.get("spec") if status == 200 else None
if spec:
    check("proposta tem entidades e telas", bool(spec.get("entities")) and bool(spec.get("screenSpecs")),
          ", ".join(e["name"] for e in spec.get("entities") or []))

status, assist = call("POST", "copilot/assist", {"field": "objective", "name": "Fisio Agenda", "idea": "agenda de fisioterapia"}, token)
check("ajuda por campo pela IA", status == 200 and assist.get("source") == "claude", (assist or {}).get("suggestion", "")[:80])

if IMAGE:
    boundary = "----nova" + uuid.uuid4().hex
    with open(IMAGE, "rb") as f:
        img = f.read()
    mime = mimetypes.guess_type(IMAGE)[0] or "image/png"
    payload = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"image\"; filename=\"esboco\"\r\nContent-Type: {mime}\r\n\r\n").encode() + img + \
              f"\r\n--{boundary}--\r\n".encode()
    status, sk = call("POST", "copilot/sketch", token=token, raw_body=payload, content_type=f"multipart/form-data; boundary={boundary}")
    check("esboço vira telas", status == 200 and bool(sk.get("screens")), f"{len((sk or {}).get('screens') or [])} telas" if status == 200 else str(sk))

if spec:
    status, project = call("POST", "generator/projects", spec, token)
    check("projeto gerado a partir da proposta", status == 201, "" if status == 201 else str(project))
    if status == 201:
        pid = project["id"]
        status, ans = call("POST", f"projects/{pid}/ask", {"question": "Onde fica a regra que muda o status da consulta?"}, token)
        check("perguntar ao projeto", status == 200 and bool(ans.get("answer")), (ans or {}).get("answer", str(ans))[:120])
        status, ins = call("GET", f"projects/{pid}/insights", token=token)
        open_props = [p for p in (ins or {}).get("proposals", []) if p["state"] == "OPEN"]
        if open_props:
            p = open_props[0]
            status, prev = call("POST", f"projects/{pid}/improvements/{p['id']}/preview",
                                {"baseVersion": ins["version"], "useAi": True, "note": "Explique a mudança em comentários."}, token)
            check("melhoria aplicada com IA (prévia)", status == 200 and prev["changeSet"]["usedAi"],
                  f"{p['title']}: " + (", ".join(c["path"] for c in prev["changeSet"]["changes"]) if status == 200 else str(prev)))
        else:
            check("melhoria aplicada com IA (prévia)", False, "o projeto não tem proposta aberta para testar")

failed = [r for r in results if r[0] == "FAIL"]
print(f"\n{len(results) - len(failed)} de {len(results)} verificações passaram.")
sys.exit(1 if failed else 0)
