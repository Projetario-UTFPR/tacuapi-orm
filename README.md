# Tacuapi ORM

Tacuapi é um protótipo de ORM e gerador de formulários HTML. Através de modelos
— classes que utilizam as anotações fornecidas pela Tacuapi ORM —, a biblioteca
gera o HTML de _inputs_ para cada campo daquele modelo de modo a ser embutido
em páginas HTML, bem como o código SQL necessário para criar a tabela.

## Exemplo
Refira-se à aplicação [PoC]() para um exemplo da biblioteca em utilização.

## Roadmap
- [x] Gerar o HTML dos inputs.
- [x] Gerar o SQL para criar a tabela.
- [ ] Gerar o SQL para atualizar uma tabela diante de mudanças na classe.

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
