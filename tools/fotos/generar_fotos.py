"""Las caras de la semilla: quién lleva foto y cómo se hizo cada una (workforce-loader#19).

Esto NO lo ejecuta el loader. Se ejecuta una vez, a mano, en una máquina con GPU, y lo que deja
—los JPEG de src/main/resources/fotos/ y la lista tools/fotos/poblacion.txt— se commitea. Los JPEG
son la verdad; este script es su procedencia. El loader sólo sube los ficheros que encuentre.

Son dos pasos, y van por separado a propósito:

    python tools/fotos/generar_fotos.py poblacion --base b4rrhh_ref178 --semilla demo-seed-v178-b5cd051.dump
    python tools/fotos/generar_fotos.py caras

El primero lee la semilla (una copia restaurada, por docker exec contra el contenedor de Postgres) y
escribe poblacion.txt. El segundo lee SÓLO poblacion.txt: para regenerar las caras no hace falta
ninguna base, y lo que decide a quién se le pone cara queda escrito en el repo y no en una consulta.

MODELO
    Stable Diffusion XL base 1.0, de Stability AI.
    https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0
    Revisión fijada: 462165984030d82259a11f4367a4eed129e94a7b (la última, del 30/10/2023).
    Pesos en fp16 (variant="fp16"), unos 7 GB la primera vez, a la caché de Hugging Face.

LICENCIA
    CreativeML Open RAIL++-M. Licencia de copyright perpetua, mundial, gratuita, con uso
    comercial y como servicio. Sobre lo generado: «Licensor claims no rights in the Output You
    generate using the Model». Las restricciones de uso (anexo A) son sobre dañar, difamar,
    discriminar, datos personales que perjudiquen a alguien, etc.; un retrato sintético de nadie
    para una demo no entra en ninguna. Las caras no son de nadie: ni stock ni personas reales,
    porque en una demo pública eso sería poner la cara de alguien que existe al lado de un DNI
    inventado y una nómina.

    Elegido frente a FLUX.1-schnell (Apache 2.0, mejor licencia todavía) porque FLUX son 12 000
    millones de parámetros y no cabe en los 12 GB de la RTX 2060 sin descargar a CPU, y frente a
    SD 3.5 (licencia comunitaria con tope de facturación). SDXL cabe entero en la tarjeta.

MÁQUINA
    Hecho en una NVIDIA GeForce RTX 2060 de 12 GB, CUDA 13.1, con torch 2.7.1+cu128,
    diffusers 0.34.0, transformers 4.53.2 y accelerate 1.8.1 en un entorno aparte:

        python -m venv venv-fotos
        venv-fotos/Scripts/pip install torch==2.7.1 --index-url https://download.pytorch.org/whl/cu128
        venv-fotos/Scripts/pip install diffusers==0.34.0 transformers==4.53.2 accelerate==1.8.1 safetensors pillow
        venv-fotos/Scripts/python tools/fotos/generar_fotos.py caras

    Si no cabe en la tarjeta, --ahorrar-memoria descarga a CPU lo que no se esté usando (más lento).

PROMPT
    El mismo para todos, salvo sexo y edad. Lo que hace que una plantilla parezca una plantilla es
    la homogeneidad —mismo fondo, mismo encuadre, misma luz—, no la calidad de cada foto. Está más
    abajo, en PROMPT y PROMPT_NEGATIVO, tal cual se usa.

DETERMINISMO
    La seed del generador sale de la matrícula (SHA-256), y la franja de edad también: regenerar
    EMP000025 en la misma máquina da la misma cara. Entre máquinas o tarjetas distintas no es
    reproducible bit a bit, y por eso los JPEG van al repositorio.

SEXO
    employee.employee no tiene sexo ni fecha de nacimiento. Se deduce del nombre de pila con la
    tabla NOMBRES, que tiene los 42 de la semilla. Ante un nombre que no esté en la tabla el script
    se niega a generar nada: no adivina. Una Carmen con barba canta.

FORMATO
    Se genera a 1024×1024 (la resolución nativa de SDXL; a 512 las caras salen peor) y se reduce a
    512×512, JPEG calidad 85. El objetivo son 60 KB por foto; si una se pasa, se baja la calidad de
    5 en 5 hasta 70 y el script dice cuál.
"""

from __future__ import annotations

import argparse
import hashlib
import subprocess
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
POBLACION = Path(__file__).resolve().parent / "poblacion.txt"
FOTOS = RAIZ / "src" / "main" / "resources" / "fotos"

MODELO = "stabilityai/stable-diffusion-xl-base-1.0"
REVISION = "462165984030d82259a11f4367a4eed129e94a7b"

PROMPT = (
    "professional corporate headshot photograph of a {edad} year old Spanish {sexo}, "
    "head and shoulders, facing the camera, natural friendly expression, business casual clothes, "
    "plain light grey seamless studio background, soft even diffused lighting, "
    "sharp focus, 85mm lens, high detail skin, photorealistic"
)
PROMPT_NEGATIVO = (
    "text, watermark, logo, signature, letters, badge, name tag, frame, border, "
    "cartoon, illustration, painting, 3d render, anime, "
    "sunglasses, hat, hands, multiple people, cropped head, "
    "deformed, distorted face, asymmetric eyes, blurry, low quality, oversaturated"
)
PASOS = 30
GUIA = 6.0
LADO_GENERADO = 1024
LADO_FINAL = 512
CALIDAD = 85
CALIDAD_MINIMA = 70
OBJETIVO_BYTES = 60 * 1024

# Los 42 nombres de pila de la semilla, tal como están en employee.employee (sin tildes).
# F = mujer, M = hombre. Un nombre que no esté aquí para el script: no se adivina.
NOMBRES = {
    "Alberto": "M", "Alejandro": "M", "Ana": "F", "Ana Maria": "F", "Andres": "M",
    "Antonio": "M", "Beatriz": "F", "Carlos": "M", "Carmen": "F", "Cristina": "F",
    "Daniel": "M", "David": "M", "Dolores": "F", "Elena": "F", "Francisca": "F",
    "Francisco": "M", "Francisco Javier": "M", "Ines": "F", "Isabel": "F", "Javier": "M",
    "Jorge": "M", "Jose": "M", "Jose Antonio": "M", "Jose Luis": "M", "Juan": "M",
    "Juan Antonio": "M", "Laura": "F", "Lucia": "F", "Manuel": "M", "Maria": "F",
    "Maria del Carmen": "F", "Marta": "F", "Miguel": "M", "Nuria": "F", "Pablo": "M",
    "Paula": "F", "Pilar": "F", "Raquel": "F", "Rocio": "F", "Ruben": "M",
    "Sara": "F", "Sergio": "M",
}
SEXO_EN_EL_PROMPT = {"F": "woman", "M": "man"}

# Sin fecha de nacimiento, una franja por hash de la matrícula, repartida entre las cuatro.
# En el prompt va la mitad de la franja: «40 year old» envejece mejor que «35 to 45».
FRANJAS = [(25, 35), (35, 45), (45, 55), (55, 62)]

# --- Población ------------------------------------------------------------------------------
#
# Cómo pagina el directorio (mirado en el código el 17/10/2026, no supuesto):
#   - de 50 en 50: EMPLOYEE_DIRECTORY_PAGE_SIZE en el frontend y DEFAULT_SIZE en
#     ListEmployeesService del backend;
#   - sin filtro de estado por defecto (status: null), así que salen activos y bajas;
#   - ordenado por rule_system_code, employee_type_code, employee_number
#     (SpringDataEmployeeRepository). En la semilla todo es ESP/INTERNAL.
# Así que las dos primeras páginas son las cien primeras matrículas, EMP000001-EMP000100.
PAGINA = 50
PAGINAS = 2

# Los que nombra el guion de workspace#18 (comentario 3758). Los dos caen ya en la primera página,
# pero se anotan aparte: si un día el directorio cambia de orden, estos siguen teniendo que salir.
GUION = ["EMP000001", "EMP000025"]

CENTROS = ["BRANCH_EAST", "BRANCH_NORTH", "BRANCH_SOUTH", "MAIN_OFFICE"]
ACTIVOS_POR_CENTRO = 13
BAJAS_POR_CENTRO = 2

# El estado se mide a una fecha fija y no a hoy: la lista no puede cambiar según el día en que se
# ejecute. Es el último día del mes que la semilla deja abierto (loader.period = 202609).
FECHA_DEL_ESTADO = "2026-09-30"

CONSULTA = f"""
with ultimo_centro as (
  select distinct on (w.employee_id) w.employee_id, w.work_center_code
  from employee.work_center w
  where w.start_date <= date '{FECHA_DEL_ESTADO}'
  order by w.employee_id, w.start_date desc, w.work_center_assignment_number desc
)
select e.employee_number, e.first_name, coalesce(c.work_center_code, '-'),
  case
    when exists (select 1 from employee.presence p where p.employee_id = e.id
                 and p.start_date <= date '{FECHA_DEL_ESTADO}'
                 and (p.end_date is null or p.end_date >= date '{FECHA_DEL_ESTADO}')) then 'ACTIVE'
    when exists (select 1 from employee.presence p where p.employee_id = e.id
                 and p.end_date < date '{FECHA_DEL_ESTADO}') then 'TERMINATED'
    else 'NOT_HIRED'
  end
from employee.employee e
left join ultimo_centro c on c.employee_id = e.id
where e.rule_system_code = 'ESP' and e.employee_type_code = 'INTERNAL'
order by e.rule_system_code, e.employee_type_code, e.employee_number
"""


def leer_plantilla(contenedor: str, base: str) -> list[tuple[str, str, str, str]]:
    salida = subprocess.run(
        ["docker", "exec", contenedor, "psql", "-U", "b4rrhh", "-d", base, "-w",
         "-At", "-F", "|", "-v", "ON_ERROR_STOP=1", "-c", CONSULTA],
        check=True, capture_output=True, text=True, encoding="utf-8",
    ).stdout
    filas = [tuple(linea.split("|")) for linea in salida.splitlines() if linea.strip()]
    if not filas:
        sys.exit(f"La base {base} no tiene empleados ESP/INTERNAL: ¿es una copia de la semilla?")
    return filas  # type: ignore[return-value]


def con_paso(lista: list, cuantos: int) -> list:
    """Cuantos elementos repartidos con un paso fijo, empezando por el primero."""
    if len(lista) < cuantos:
        sys.exit(f"Se piden {cuantos} y sólo hay {len(lista)}: la semilla no es la que se espera.")
    paso = len(lista) // cuantos
    return [lista[i * paso] for i in range(cuantos)]


def poblacion(args: argparse.Namespace) -> None:
    plantilla = leer_plantilla(args.contenedor, args.base)
    motivos: dict[str, list[str]] = {}
    filas = {fila[0]: fila for fila in plantilla}

    for posicion, fila in enumerate(plantilla[: PAGINA * PAGINAS]):
        motivos.setdefault(fila[0], []).append(f"directorio, página {posicion // PAGINA + 1}")
    for matricula in GUION:
        if matricula not in filas:
            sys.exit(f"{matricula} lo nombra el guion de workspace#18 y no está en la base.")
        motivos.setdefault(matricula, []).append("guion de workspace#18")

    resto = plantilla[PAGINA * PAGINAS:]
    for centro in CENTROS:
        del_centro = [f for f in resto if f[2] == centro]
        activos = [f for f in del_centro if f[3] == "ACTIVE"]
        bajas = [f for f in del_centro if f[3] == "TERMINATED"]
        for fila in con_paso(activos, ACTIVOS_POR_CENTRO):
            motivos.setdefault(fila[0], []).append(f"muestra de {centro}, activo")
        for fila in con_paso(bajas, BAJAS_POR_CENTRO):
            motivos.setdefault(fila[0], []).append(f"muestra de {centro}, baja")

    desconocidos = sorted({filas[m][1] for m in motivos} - NOMBRES.keys())
    if desconocidos:
        sys.exit("Nombres que no están en la tabla NOMBRES (no se adivina): " + ", ".join(desconocidos))

    lineas = [
        "# Quién lleva cara en la semilla, y por qué (workforce-loader#19).",
        f"# La escribe tools/fotos/generar_fotos.py poblacion, desde la base {args.base}"
        + (f", restaurada de {args.semilla}." if args.semilla else "."),
        f"# Estado y centro medidos al {FECHA_DEL_ESTADO}. Una matrícula por línea, en su orden:",
        "# matricula|nombre|sexo|centro|estado|motivo",
    ]
    for matricula in sorted(motivos):
        _, nombre, centro, estado = filas[matricula]
        lineas.append("|".join([matricula, nombre, NOMBRES[nombre], centro, estado,
                                "; ".join(motivos[matricula])]))
    POBLACION.write_text("\n".join(lineas) + "\n", encoding="utf-8", newline="\n")
    print(f"{len(motivos)} matrículas en {POBLACION.relative_to(RAIZ)}")


def leer_poblacion() -> list[tuple[str, str]]:
    if not POBLACION.exists():
        sys.exit(f"Falta {POBLACION}: primero el paso «poblacion».")
    filas = []
    for linea in POBLACION.read_text(encoding="utf-8").splitlines():
        if not linea or linea.startswith("#"):
            continue
        matricula, nombre, *_ = linea.split("|")
        filas.append((matricula, nombre))
    desconocidos = sorted({nombre for _, nombre in filas} - NOMBRES.keys())
    if desconocidos:
        sys.exit("Nombres que no están en la tabla NOMBRES (no se adivina): " + ", ".join(desconocidos))
    return filas


def huella(matricula: str) -> bytes:
    return hashlib.sha256(matricula.encode("utf-8")).digest()


def seed_de(matricula: str) -> int:
    return int.from_bytes(huella(matricula)[:4], "big")


def edad_de(matricula: str) -> int:
    desde, hasta = FRANJAS[huella(matricula)[4] % len(FRANJAS)]
    return (desde + hasta) // 2


def prompt_de(matricula: str, nombre: str) -> str:
    return PROMPT.format(edad=edad_de(matricula), sexo=SEXO_EN_EL_PROMPT[NOMBRES[nombre]])


def guardar(imagen, destino: Path) -> tuple[int, int]:
    import io

    reducida = imagen.convert("RGB").resize((LADO_FINAL, LADO_FINAL), resample=1)  # LANCZOS
    calidad = CALIDAD
    while True:
        buffer = io.BytesIO()
        reducida.save(buffer, format="JPEG", quality=calidad, optimize=True, progressive=True)
        if buffer.tell() <= OBJETIVO_BYTES or calidad <= CALIDAD_MINIMA:
            destino.write_bytes(buffer.getvalue())
            return buffer.tell(), calidad
        calidad -= 5


def caras(args: argparse.Namespace) -> None:
    filas = leer_poblacion()
    pendientes = [(m, n) for m, n in filas if args.rehacer or not (FOTOS / f"{m}.jpg").exists()]
    if args.solo:
        pendientes = [(m, n) for m, n in filas if m in set(args.solo)]
    print(f"{len(filas)} en la población, {len(pendientes)} por generar en {FOTOS.relative_to(RAIZ)}")
    if not pendientes:
        return

    import torch
    from diffusers import StableDiffusionXLPipeline

    if not torch.cuda.is_available():
        sys.exit("No hay CUDA: esto se ejecuta en la GPU.")
    pipe = StableDiffusionXLPipeline.from_pretrained(
        MODELO, revision=REVISION, torch_dtype=torch.float16, variant="fp16", use_safetensors=True,
    )
    if args.ahorrar_memoria:
        pipe.enable_model_cpu_offload()
    else:
        pipe.to("cuda")
    pipe.set_progress_bar_config(disable=True)

    FOTOS.mkdir(parents=True, exist_ok=True)
    for indice, (matricula, nombre) in enumerate(pendientes, start=1):
        generador = torch.Generator(device="cuda").manual_seed(seed_de(matricula))
        imagen = pipe(
            prompt=prompt_de(matricula, nombre),
            negative_prompt=PROMPT_NEGATIVO,
            num_inference_steps=PASOS,
            guidance_scale=GUIA,
            width=LADO_GENERADO,
            height=LADO_GENERADO,
            generator=generador,
        ).images[0]
        tamano, calidad = guardar(imagen, FOTOS / f"{matricula}.jpg")
        aviso = "" if calidad == CALIDAD else f"  (calidad {calidad} para no pasar de 60 KB)"
        print(f"[{indice}/{len(pendientes)}] {matricula} {nombre}, {edad_de(matricula)} años:"
              f" {tamano // 1024} KB{aviso}", flush=True)


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")  # la consola de Windows no es UTF-8
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    pasos = parser.add_subparsers(dest="paso", required=True)

    p = pasos.add_parser("poblacion", help="escribe poblacion.txt desde una copia de la semilla")
    p.add_argument("--base", required=True, help="la base con la semilla restaurada")
    p.add_argument("--semilla", help="el volcado publicado del que sale la base, para la cabecera")
    p.add_argument("--contenedor", default="b4rrhh-postgres")
    p.set_defaults(accion=poblacion)

    c = pasos.add_parser("caras", help="genera los JPEG que falten de poblacion.txt")
    c.add_argument("--rehacer", action="store_true", help="regenera también las que ya existen")
    c.add_argument("--solo", nargs="+", metavar="MATRICULA", help="sólo estas matrículas")
    c.add_argument("--ahorrar-memoria", action="store_true", help="descarga a CPU lo que no se usa")
    c.set_defaults(accion=caras)

    args = parser.parse_args()
    args.accion(args)


if __name__ == "__main__":
    main()
