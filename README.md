# b4rrhh-workforce-loader

CLI externo para crear empleados de forma masiva contra las APIs publicas canonicas de B4RRHH.

## Ejecutar

```bash
mvn spring-boot:run
```

## Dry-run

**En seco por omision: un `mvn spring-boot:run` sin tocar nada NO escribe.** Recorre la
simulacion entera, pide los catalogos al backend y saca su informe, pero no manda ni un alta.

Para escribir de verdad hace falta decirlo, y mejor en la linea de ordenes, que gana a todo y no
depende de en que estado dejaste el fichero:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--loader.run.dry-run=false
```

El defecto era `false` —o sea, escribia— y este apartado decia lo contrario durante meses. Lo
que decide el cambio no es la simetria con el documento, que ya estaba arreglada, sino el
reparto de costes (`workforce-loader#9`): con `false`, equivocarse cuesta una base escrita y una
hora de reconstruirla; con `true`, una bandera que hay que volver a poner. **Cuando los dos
errores no cuestan igual, el defecto va del lado barato.**

Y la guarda del `workforce-loader#8` no cubria esto: comprueba **a donde** se escribe, no **si**
se escribe. Con las expectativas bien puestas, una corrida que alguien creia en seco escribia mil
empleados en la base correcta y el informe salia perfecto.

**Un informe perfecto no prueba que haya escrito.** Lo prueba el recuento en la base:

```bash
docker exec b4rrhh-postgres psql -U b4rrhh -d <la base> -tAc \
  'select count(*) from employee.employee'
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
