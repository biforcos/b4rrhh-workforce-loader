# b4rrhh-workforce-loader

CLI externo para crear empleados de forma masiva contra las APIs publicas canonicas de B4RRHH.

## Ejecutar

```bash
mvn spring-boot:run
```

## Dry-run

Por defecto `loader.run.dry-run: true`, por lo que se generan empleados y payloads de hire sin invocar el backend.
Para ejecutar llamadas reales, configurar `loader.run.dry-run: false` en `application.yml`.

## A que base escribe

Antes de generar nada, el loader le pregunta al backend a que base esta conectado y lo compara
con lo que la corrida declara. Si no coincide, no arranca y lo dice nombrando las dos
(`workforce-loader#8`).

No hay valor por defecto: solo la corrida sabe cual era la respuesta buena.

```yaml
loader:
  backend:
    expected:
      database: localhost:5432/b4rrhh_wl7   # host:puerto/nombre
      employees: 0                          # los que tiene que haber ya
      schema-version: 120                   # opcional
```

o por entorno, sin tocar el fichero:

```powershell
$env:LOADER_BACKEND_EXPECTED_DATABASE  = "localhost:5432/b4rrhh_wl7"
$env:LOADER_BACKEND_EXPECTED_EMPLOYEES = "0"
```

Lo que hay que poner se lo pregunta uno al backend arrancado, con un token de ADMIN:

```
GET http://localhost:8080/api/system/target
{ "database": "localhost:5432/b4rrhh_wl7", "schemaVersion": "120", "employees": 0 }
```

El nombre de la base lleva host y puerto a proposito: la base de la demo y la de desarrollo se
llaman las dos `b4rrhh`, asi que el nombre suelto no distingue el caso que mas dano hace.

La comprobacion se repite cada `loader.backend.recheck-every-writes` escrituras (200 por
defecto). Comprobarlo solo al arrancar no basta: el backend del otro lado se puede sustituir a
mitad de corrida.
