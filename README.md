# Buscaminas distribuido TCP

Laboratorio de Computación en Internet I (ICESI). Un servidor TCP multihilo mantiene **un único tablero de
Buscaminas compartido** y varios clientes de consola juegan sobre él enviando peticiones JSON.

```
clase_tcp_udp/
├── server/   co.icesi.buscaminas.Main                (servidor TCP + ThreadPool de 5 hilos)
└── client/   co.icesi.buscaminas.client.MainClient   (cliente de consola)
```

## Requisitos

- JDK 17 o superior (probado con JDK 23). No hace falta instalar Gradle: se usa el wrapper (Gradle 8.10.2).
- Gson 2.10.1 se descarga automáticamente desde Maven Central.

## Compilación

```bash
./gradlew build          # Windows: gradlew.bat build
```

## Ejecución

Servidor (el puerto es opcional, por defecto 12345; escucha en todas las interfaces, `0.0.0.0`):

```bash
./gradlew :server:run --args="12345"
```

Cliente (host y puerto opcionales, por defecto `localhost 12345`). Abrir una terminal por cliente:

```bash
./gradlew :client:run --args="localhost 12345" --console=plain
```

Para jugar desde otra máquina de la red, reemplazar `localhost` por la IP del servidor (y permitir el puerto en el
firewall). `--console=plain` evita que la barra de progreso de Gradle tape el menú.

Opción de depuración: `./gradlew :server:run --args="12345 --local"` lanza además el juego por consola sobre el
mismo tablero, en el propio servidor.

### Menú del cliente

```
1. Iniciar nuevo juego
2. Seleccionar celda
3. Marcar/desmarcar celda
4. Ver tablero
5. Rendirse (mostrar todo)
6. Salir
```

Símbolos del tablero: `[ . ]` oculta, `[ M ]` bandera (amarillo), `[ * ]` mina (rojo), `[ n ]` número de minas
alrededor, `[   ]` celda vacía.

## Protocolo

- Una conexión TCP por petición: el cliente envía **una línea** JSON terminada en `\n` y el servidor responde con
  **una línea** JSON y cierra la conexión. Codificación UTF-8.
- El servidor siempre responde, incluso ante errores (JSON inválido, acción desconocida, parámetros inválidos o
  si no llega el salto de línea en 10 s).

Petición:

```json
{"action": "SELECT_CELL", "data": {"i": "2", "j": "3"}}
```

Respuesta:

```json
{"status": "OK", "data": {"win": false, "gameEnd": false, "board": [[{"isLandMine": false, "value": 1, "hide": true, "showAll": false, "isMarked": false}, "..."]]}}
```

```json
{"status": "ERROR", "data": {"message": "Cell no valid: (9,9) está fuera del tablero 8x8"}}
```

### Catálogo de acciones

| Acción        | `data` de la petición             | `data` de la respuesta (status OK)                                   |
|---------------|-----------------------------------|----------------------------------------------------------------------|
| `INIT_GAME`   | `n`, `m`, `minas` (n, m ∈ [1,100]; 0 < minas < n·m) | `board`                                   |
| `SELECT_CELL` | `i`, `j`                          | `board`, `win`, `gameEnd`, `message` (en victoria o derrota)         |
| `MARK_CELL`   | `i`, `j`                          | `board` (alterna la bandera solo si la celda está oculta)            |
| `GET_BOARD`   | —                                 | `board`                                                              |
| `SOW_ALL`     | —                                 | `board` con todas las celdas visibles                                |

Resultados de `SELECT_CELL`:

- Celda segura: `status OK`, `gameEnd=false` (o `gameEnd=true, win=true` si era la última celda segura).
- Mina: `status OK`, `gameEnd=true`, `win=false`, `message "Game over"`. El cliente pide `SOW_ALL` y muestra
  el tablero completo.
- Coordenada fuera del tablero: `status ERROR` con `message`; la partida **no** termina.
- Las celdas con bandera no se destapan (hay que quitar la marca primero).

Cada celda del tablero tiene los campos `isLandMine`, `value`, `hide`, `showAll` e `isMarked`.

### Tablero compartido y concurrencia

**Todos los clientes comparten el mismo tablero**: si un cliente inicia un juego nuevo, marca o destapa una
celda, los demás lo ven en su siguiente petición (opción 4 para refrescar).

- El servidor atiende cada conexión en un `ExecutorService` de 5 hilos (`pool-1-thread-1` … `pool-1-thread-5`).
- Las operaciones de `BoardGame` son `synchronized`, y el handler ejecuta la operación y la serialización JSON
  dentro de `synchronized (services.getGame())`, de modo que cada respuesta es un snapshot consistente.
- Cada línea del log del servidor incluye el hilo, la IP:puerto del cliente y la acción recibida:

```
[pool-1-thread-2] Cliente conectado: 127.0.0.1:63907
[pool-1-thread-2] Acción recibida de 127.0.0.1:63907: MARK_CELL {i=1, j=1}
[pool-1-thread-2] Respuesta enviada a 127.0.0.1:63907: status=OK. Cliente desconectado
```

## Verificación realizada

Con el servidor levantado mediante `./gradlew :server:run --args="12345"`:

- Las 5 acciones (`INIT_GAME`, `SELECT_CELL`, `MARK_CELL`, `GET_BOARD`, `SOW_ALL`) responden `OK`; se
  comprobó una victoria (destapando todas las celdas seguras) y una derrota (seleccionando una mina).
- Casos inválidos (JSON roto, línea vacía, acción desconocida, coordenadas fuera de rango, parámetros no
  numéricos o ausentes, `INIT_GAME` con minas o dimensiones inválidas y cliente que no envía `\n`) responden
  `ERROR` con `message`.
- En todos los casos la respuesta es exactamente una línea JSON.
- 12 conexiones en paralelo y 2 clientes Java simultáneos: todas respondidas, atendidas por los 5 hilos del pool.

## Evidencias

Capturas sugeridas para el informe:

1. **Servidor arrancando**: la línea `[main] Servidor TCP escuchando en 0.0.0.0:12345 (pool de 5 hilos)`.
2. **Partida completa**: cliente iniciando un juego (opción 1), destapando varias celdas (opción 2) y el tablero
   con números y zonas vacías.
3. **Bandera**: una celda marcada `[ M ]` en amarillo (opción 3) y, tras marcarla de nuevo, desmarcada.
4. **Victoria**: el mensaje `¡FELICITACIONES! Ganaste la partida.` con el tablero final (un tablero pequeño, p. ej.
   3x3 con 1 mina, facilita ganar).
5. **Derrota**: el mensaje `¡BOOM! Pisaste una mina.` y el tablero completo con las minas `[ * ]` en rojo.
6. **Validaciones**: una entrada no numérica o fuera de rango en el cliente y un `Error: ...` devuelto por el
   servidor.
7. **Dos clientes simultáneos** en terminales separadas, mostrando que un cambio hecho por uno aparece en el otro
   (tablero compartido).
8. **Log del servidor con varios hilos**: líneas de `pool-1-thread-1`, `pool-1-thread-2`, … atendiendo a
   clientes con distintos puertos de origen.
