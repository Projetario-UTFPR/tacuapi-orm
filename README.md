# Tacuapi ORM

Tacuapi é um protótipo de ORM e gerador de formulários HTML. Através de modelos
— classes que utilizam as anotações fornecidas pela Tacuapi ORM —, a biblioteca
gera o HTML de _inputs_ para cada campo daquele modelo de modo a ser embutido
em páginas HTML, bem como o código SQL necessário para criar a tabela.

## Exemplo
Refira-se à aplicação [PoC]() para um exemplo da biblioteca em utilização.

## PoC: servidor HTTP + PostgreSQL

A aplicação PoC (`poc-example`) é um servidor HTTP mínimo — construído sobre o
`com.sun.net.httpserver` que já vem na JDK, sem dependências extras — que faz
o caminho completo:

1. **conecta** no PostgreSQL (conexão _hardcoded_, com exceção de variáveis de
   ambiente que sobrescrevem o padrão);
2. **migra**: cria o esquema a partir dos modelos (`Tucuapi.generateSqlSetupStatements`)
   na primeira execução e, em todas elas, compara a classe com o banco para
   **atualizar** a tabela (`Tucuapi.generateSqlUpdateStatements`);
3. **serve**: a rota `GET /` devolve o formulário HTML gerado a partir do modelo.

```kotlin
MiniHttpServer(port)
    .route("/") { Response("<html><body>${Tucuapi.generateHtmlForm(User::class)}</body></html>") }
    .start()
```

### Com Docker (recomendado)

O [`Dockerfile`](Dockerfile) monta um container único com o PostgreSQL, o JRE e a
aplicação — o `docker/start.sh` sobe o banco, espera ele ficar pronto e então
sobe a aplicação.

```bash
docker build -t tacuapi-poc .
docker run --rm -p 8080:8080 -p 5432:5432 tacuapi-poc # expõe ambos o server HTTP e o banco de dados
```

Depois é só abrir <http://localhost:8080> no navegador (ou `curl http://localhost:8080`).

Para manter o banco entre execuções, monte um volume no diretório de dados do
PostgreSQL: o esquema é criado apenas na primeira execução, mas a conferência
com a classe continua rodando a cada start:

```bash
docker volume create tacuapi-data
docker run --rm -p 8080:8080 -v tacuapi-data:/var/lib/postgresql/data tacuapi-poc
```

### Sem Docker

Suba apenas o PostgreSQL e rode a aplicação pela Gradle:

```bash
docker run -d --name tacuapi-pg -p 5432:5432 \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=tacuapi \
  postgres:16-alpine

./gradlew :poc-example:run
```

### Observações

- A rota `POST /salvar` — para onde o formulário aponta — **não** está
  implementada nesta PoC: o escopo é gerar a tabela e servir o formulário.
- As variáveis `TACUAPI_DB_URL`, `TACUAPI_DB_USER`, `TACUAPI_DB_PASSWORD` e
  `TACUAPI_PORT` sobrescrevem os valores _hardcoded_ quando definidas.

## Atualização de tabelas

Quando a classe do modelo muda, a biblioteca compara a "versão desejada" do
esquema (gerada a partir da classe) com a "versão atual" (lida do banco) e
gera os comandos `alter table` que levam uma à outra.

```kotlin
// lendo o esquema atual do PostgreSQL
val current = PostgresSchema.readTableSchema(connection, "users")

// comparando com a classe
val statements = Tucuapi.generateSqlUpdateStatements(User::class, listOf(current))
// ou, direto do banco:
// val statements = Tucuapi.generateSqlUpdateStatements(connection, User::class)
```

O que é comparado:

| Mudança na classe | Comando gerado |
| --- | --- |
| campo novo | `alter table users add column nickname varchar(255)` |
| campo removido | `alter table users drop column nickname` |
| tipo diferente | `alter table users alter column likes type bigint using likes::bigint` |
| campo passou a ser opcional | `alter table users alter column nickname drop not null` |
| campo passou a ser obrigatório | `alter table users alter column name set not null` |
| valor padrão novo | `alter table users alter column likes set default 0` |
| valor padrão removido | `alter table users alter column likes drop default` |
| `unique` novo | `alter table users add constraint users_email_key unique (email)` |
| `unique` removido | `alter table users drop constraint users_email_key` |
| chave primária trocada | `drop constraint users_pkey` + `add constraint users_pkey primary key (id)` |

Detalhes:

- O esquema desejado é gerado por `Tucuapi.describeSchemas(model)`, que também
  cuida das tabelas intermediárias dos campos `@Multivalued`.
- As operações destrutivas (remover coluna ou restrição) podem ser desligadas
  com `allowDestructive = false`.
- A comparação ignora as diferenças de nomenclatura entre o SQL gerado e o que
  o PostgreSQL devolve (`varchar(200)` × `character varying(200)`,
  `timestamptz` × `timestamp with time zone`, etc.).
- A classe é a fonte da verdade: restrições do banco que não estão na classe
  são removidas.

## Roadmap
- [x] Gerar o HTML dos inputs.
- [x] Gerar o SQL para criar a tabela.
- [x] Gerar o SQL para atualizar uma tabela diante de mudanças na classe.

## Anotações

### `@NotNull`
Não existe! O Tucuapi ORM verifica se o tipo do campo é opcional para decidir se
ele poderá ou não ser nulo.


### `@ModelEnum(name = "user_role_e")`
Anota um _enum_.

- Gera um formulário com _select_ simples com todas as variantes do enum.
- Gera um _enum_ no banco de dados com todas as variantes do enum.

### `@ModelEnumVariant(value = "admin", label = "Administrador")`
Anota uma variante de um _enum_.

Diz como interpretar uma variante de um enum. Quando não presente, a própria
variante será o seu valor e seu "nome".
- No formulário, apresenta essa opção como `label` com valor `value`.
- No banco de dados, interpreta a variante como `value`.

### `@Model(name = "users")`
Anota uma classe.

Diz que a classe é um modelo.
- Viabiliza a criação de formulário HTML para criar um registro do modelo.
- Fornece o SQL para criar a tabela em um banco de dados.

### `@PrimaryKey`
Anota um campo de uma classe.

Diz que o campo é uma das chaves primárias (ou a única chave primária) da tabela
no banco de dados.

Implica que o campo é não-nulo.

### `@InputOmit`
Anota um campo de uma classe.

Impede que aquele campo gere um _input_ no formulário HTML.

### `@VarcharConfig(size = 200)`
Anota um campo de uma classe.

Reforça que o tipo do dado é uma _string_. É opcional, visto que pode ser inferido
que é uma _string_ pelo tipo do campo da classe.

Permite especificar o tamanho máximo:
- Gera o tipo `varchar(<SIZE>)` no SQL.
- Limita o tamanho do _input_ a _size_ e força o tipo do _input_ ser `"text"`.

### `@Column`
Anota um campo de uma classe.

- Permite dizer se o campo é único.
    - Gera a cláusula `unique` no SQL da tabela.
- Permite dizer o nome do campo no banco de dados.
- Permite forçar o tipo da coluna (via `type`).
- Permite adicioanr um valor padrão no esquema da tabela (via `default`).
  - O valor de `default` será colocado diretamente no SQL _statement_. Logo, se o valor for uma _string_,
    é necessário colocar as aspas simples na _string_ (por exemplo, `default = "'foo'"`A.)

### `@DateTime(timezoneOffset = true)`
Anota um campo de uma classe.

- Diz que o tipo do campo há de ser TimeZone ou equivalente no banco de dados.
    - Se `timezoneOffset = true`, força o tipo a suportar zona de fuso horário.
- Força o tipo do _input_ ser `"datetime-local"`.

### `@Multivalued`
Anota um campo de uma classe.

- Faz gerar uma tabela intermediária para armazenar valores em relação 1:N.
- Faz gerar um _input select_ que suporta múltiplas escolhas.

### `@Text`
Anota um campo de uma classe.

- Faz o tipo da coluna ser `text` no SQL.
- Faz o _input_ HTML ser um elemento `textarea`.

## Observações da Linguagem

### Singletones
Declarar _singletones_ é trivial em Kotlin: basta declará-lo como um `object` ao invés de uma classe.
A sintaxe do `object` permite, inclusive, implementar interfaces ou herdar classes de verdade, porém aquele objeto
é a sua única instância em toda a _runtime_, é iniciado sob-demanda (_lazily) e é _thread-safe_.

### Acesso encadeado a recursos nulos
O Kotlin oferece vários _syntax-sugars_ pra acessar variáveis que podem ser nulas:
- `?:` - é o equivalente ao _null-coalesce_ do JavaScript, ele diz que se o operando à esquerda for `null`, que se utilize
    o operando à direita como valor de _fallback_.
- `<expressão>?.<sequência de chamadas>` (_safe call operator_) - apenas seque `sequência de chamadas` se o resultado de `expressão` não for `null`.
- `.let {}` (_scope function_) - é próximo a uma chamada do método `map`, porém o valor é acessado através da variável implícita `it`, e o
    bloco de código (`{}`) só é executado se o valor não for nulo. É quase que o equivalente ao `Option::map` do Rust.
    Outro ponto é que, diferente do `Option::map`, aqui você pode usar o _early return_.

Usar o _safe call operator_ com o _scope function_ permite reescrever vários `if (val != null) { return X }` numa
mesma linha e com a chamada escrita flúida, por exemplo: `val?.let { return X }`. (Observe que, sempre que
um bloco de código é o último ou único parâmetro de uma função, não precisamos envolver o bloco de código entre
parênteses.)
