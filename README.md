# b4rrhh-workforce-loader

CLI externo para crear empleados de forma masiva contra las APIs publicas canonicas de B4RRHH.

## Ejecutar

```bash
mvn spring-boot:run
```

## Dry-run

**`application.yml` trae hoy `loader.run.dry-run: false`: el loader ESCRIBE.** Un
`mvn spring-boot:run` sin tocar nada da mil altas reales contra el backend al que apunte la
configuracion.

Este apartado decia lo contrario —«por defecto `true`, sin invocar el backend»— y esa frase es
peligrosa de una forma concreta: alguien la lee, lanza el loader **para ver que pasaria**, y
escribe. No es el escenario del `workforce-loader#8`, donde la guarda de la base avisa: aqui la
escritura iria a la base correcta y el informe saldria perfecto (`b4rrhh/workspace#3`).

Para no escribir, `loader.run.dry-run: true` en `application.yml`, o mejor en la linea de
ordenes, que gana a todo y no depende de en que estado dejaste el fichero:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--loader.run.dry-run=true
```

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
