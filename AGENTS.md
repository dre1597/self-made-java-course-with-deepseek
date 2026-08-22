Curso de Java — veja [README.md](./README.md) pra contexto completo.

Siga o AGENTS.md global (~/.config/opencode/AGENTS.md) acima de tudo. Regras específicas do projeto:
- Leia SEMPRE o arquivo antes de editar — eu posso ter mexido enquanto você não via
- Código sempre em inglês: nomes de classes, métodos, variáveis, testes. Textos de interface/mensagens podem ser em português
- Java 25 LTS via asdf, setado na raiz do projeto. Na dúvida sobre rodar ou compilar qualquer coisa, PERGUNTE antes. No geral não precisa rodar nada; só se quiser validar que os exemplos do curso funcionam
- Build com Gradle (wrapper). Nada de Maven a menos que o módulo de build peça explicitamente
- Exercícios vão DENTRO de cada módulo, no final. Nada de pasta de exercícios separada
- Sempre inclua testes de borda e stress nos exemplos, não só os casos felizes
- Todo código dos exemplos é código de produção: nomes descritivos em variáveis, métodos, classes, parâmetros e arquivos. NUNCA comente o óbvio nem repita o nome do método no comentário. Comentário só quando explica o porquê que o código não conta
- Boas práticas e convenções do Java (nomenclatura, imutabilidade, null-safety, foco em legibilidade) valem em TODO exemplo, mesmo nos didáticos
- Se eu pedir "faça os testes", faça SÓ os testes. Se eu pedir "arruma X", arrume SÓ X
- A pasta `labs/` guarda o código reproduzido e testado durante o curso, separado por aula (`labs/class1/`, `labs/class2/`...). Ao pedirem pra "jogar" um exemplo pra `labs/`, criar ou usar a subpasta da aula correspondente
- Curso completo e geral: cada tópico da linguagem tem seu módulo, do básico ao avançado. Nada de pular tópico
- NUNCA ensine conceito universal de programação (o que é constante, classe, herança, POO, loop). Esses conceitos o usuário já domina. Ensine como CADA UM funciona no Java: sintaxe, idioma, armadilhas, o que difere de outras linguagens
- NUNCA faça suposição sobre o histórico do usuário (quando ele estudou, o que aprendeu, qual versão usou). Escreva pro público como "quem conhece programação, mas está revisitando Java de forma completa"
- Curso cobre Java 25 LTS. Onde algo mudou entre versões (8, 11, 17, 21, 25), cite o número da versão com o que mudou. Não ancore o curso num perfil de versões específico
- Compare com TypeScript (linguagem do dia a dia do usuário) sempre que fizer sentido. Em segundo plano, vale uma comparada rápida com Ruby, Python, Scala, Kotlin, C#, PHP, C e C++ — sem virar aula dessas linguagens
- Nada de artifícios de cursinho pra iniciante: sem "pontas soltas pra explorar", sem perguntinhas retóricas, sem storytelling. Direto ao conteúdo
- NUNCA pergunte "sigo pro próximo?" ou fique enchendo o saco depois de cada resposta. Quando o usuário mandar criar módulos, crie vários em sequência direto, sem perguntar a cada um. Não crie nada por conta própria sem pedido explícito — proatividade indevida é proibida. Pergunte só quando for dúvida técnica real ou decisão que mude o rumo
- Ao criar ou editar módulos: SEMPRE carregue a skill stop-slop e use busca na web pra confirmar versões, features e APIs antes de escrever