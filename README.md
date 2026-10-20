# Tacuapi ORM

Tacuapi é um protótipo de ORM e gerador de formulários HTML. Através de modelos
— classes que utilizam as anotações fornecidas pela Tacuapi ORM —, a biblioteca
gera o HTML de _inputs_ para cada campo daquele modelo de modo a ser embutido
em páginas HTML, bem como o código SQL necessário para criar a tabela.

## Exemplo
Refira-se à aplicação [PoC]() para um exemplo da biblioteca em utilização.

## Roadmap
- [ ] Gerar o HTML dos inputs.
- [ ] Gerar o SQL para criar a tabela.
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

### `@Unique`
Anota um campo de uma classe.

- Gera a cláusula `unique` no SQL da tabela.

### `@DateTime(timezoneOffset = true)`
Anota um campo de uma classe.

- Diz que o tipo do campo há de ser TimeZone ou equivalente no banco de dados.
    - Se `timezoneOffset = true`, força o tipo a suportar zona de fuso horário.
- Força o tipo do _input_ ser `"datetime-local"`.

### `@InputMultiselect`
Anota um campo de uma classe.

- Faz gerar uma tabela intermediária para armazenar valores em relação 1:N.
- Faz gerar um _input select_ que suporta múltiplas escolhas.

### `@Text`
Anota um campo de uma classe.

- Faz o tipo da coluna ser `text` no SQL.
- Faz o _input_ HTML ser um elemento `textarea`.
