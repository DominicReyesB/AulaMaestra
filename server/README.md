# API Aula Maestra — Railway + MySQL

## Despliegue en Railway (2 servicios)

Necesitas **dos** cosas en Railway, no una sola:

| Servicio | Qué es | URL que usas |
|----------|--------|----------------|
| **MySQL** | Base de datos | `mysql://...` → **solo** en variables del servidor API |
| **API (Node)** | Carpeta `server/` | `https://xxxx.up.railway.app` → **esta** va en la app Android |

### Pasos

1. **MySQL:** New → Database → MySQL.
2. **API:** New → GitHub/repo → **Root directory** = `server` (o despliega solo la carpeta `server`).
3. En el servicio **API** → **Variables** → **Add Reference** → MySQL → guarda `MYSQL_URL`.
4. API → **Settings** → **Networking** → **Generate domain** (ej. `aula-api-production.up.railway.app`).
5. Prueba en el navegador: `https://TU-DOMINIO.up.railway.app/health`  
   Debe mostrar: `{"ok":true,"db":"mysql"}`.

Si abres `mysql://...` en el navegador **siempre dará error** (no es una página web).

## App Android

En `app/build.gradle` pon **solo** la URL HTTPS del paso 4:

```gradle
buildConfigField "String", "API_BASE_URL", '"https://aula-api-production.up.railway.app/"'
```

**No** pongas `mysql://` ni usuario/contraseña de la base en la app.

## Local

```bash
cd server
cp .env.example .env
# MYSQL_URL=mysql://root:password@127.0.0.1:3306/aula_maestra
npm install
npm start
```

Emulador: `http://10.0.2.2:3000/`
