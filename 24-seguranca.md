# 24 — Segurança

Como o Java lida com segurança prática: hash de senha, criptografia, TLS,
deserialização e as decisões que separam código seguro de código que vira CVE.
Sem pânico de segurança, só o que importa pro dia a dia.

## O modelo mental

Segurança em Java é camadas. Você não "protege o app"; você decide, em cada
fronteira, o que confiar:

1. **Dado vindo de fora é inimigo**: input de HTTP, arquivo, fila, banco.
   Valide, não confie.
2. **Criptografia é pro dado que precisa sobreviver à captura**: senha em
   repouso, dado em trânsito, token que sai da sua máquina.
3. **A JVM tem defesas**: `SecurityManager` morreu (removido no JDK 24),
   mas o que sobra é a deserialização segura, o module system e o TLS por
   padrão.

## Hash de senha: a regra de ouro

**Nunca** use `MessageDigest` (MD5, SHA-256) pra senha. O SHA-256 é rápido de
propósito, e rápido é exatamente o que o atacante quer: ele testa bilhões de
senhas por segundo contra o vazamento. Senha precisa de função de hash **lenta
e com salt**:

| Algoritmo | Uso | Pra quê |
| --------- | --- | ------- |
| Argon2id | Recomendado | Memória-hard, resiste a GPU |
| bcrypt | Comum | Legado sólido, cost factor único |
| PBKDF2 | FIPS | Quando compliance exige |

O Java 25 não tem Argon2 na JDK (o JEP pra isso ainda é rascunho). Na prática:

- **bcrypt/Argon2** vem de lib: `org.mindrot:jbcrypt` ou
  `de.mkammerer:argon2-jvm`.
- **PBKDF2** existe na JDK (`SecretKeyFactory`). Se você não pode adicionar
  lib, PBKDF2 com 600.000+ iterações e salt aleatório por usuário é o mínimo
  aceitável.

```java
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordHasher {
    private static final int ITERATIONS = 600_000;
    private static final int KEY_LENGTH = 256;

    public String hash(char[] password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
            return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt)
                    + ":" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao gerar hash de senha", e);
        } finally {
            spec.clearPassword();
        }
    }

    public boolean matches(char[] password, String stored) {
        String[] parts = stored.split(":");
        int iterations = Integer.parseInt(parts[0]);
        byte[] salt = Base64.getDecoder().decode(parts[1]);
        byte[] expected = Base64.getDecoder().decode(parts[2]);

        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH);
        try {
            byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
            return MessageDigest.isEqual(actual, expected);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao validar senha", e);
        } finally {
            spec.clearPassword();
        }
    }
}
```

Regras:

- **Salt aleatório por usuário**, 16 bytes, guardado junto do hash. Sem salt,
  senhas iguais geram hash igual e rainbow table resolve.
- **`MessageDigest.isEqual`** compara em tempo constante. `String.equals`
  vaza timing (o atacante mede quantos bytes casam).
- **`char[]`, não `String`**: `String` fica imortal no heap; `char[]` pode ser
  apagado com `clearPassword()`/`Arrays.fill`.

## Criptografia simétrica: AES-GCM

Pra criptografar dado em repouso (arquivo, campo sensível), o padrão é
**AES-GCM**: autenticado (detecta adulteração) e com nonce. Nunca AES-ECB.

```java
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;

public final class AesGcm {
    private static final int NONCE_LENGTH = 12;   // 96 bits pro GCM

    public byte[] encrypt(SecretKey key, byte[] plaintext) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            new SecureRandom().nextBytes(nonce);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));

            byte[] ciphertext = cipher.doFinal(plaintext);
            // [nonce][ciphertext]
            byte[] result = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length);
            System.arraycopy(ciphertext, 0, result, nonce.length, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("falha ao criptografar", e);
        }
    }

    public byte[] decrypt(SecretKey key, byte[] data) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            System.arraycopy(data, 0, nonce, 0, NONCE_LENGTH);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            return cipher.doFinal(data, NONCE_LENGTH, data.length - NONCE_LENGTH);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao descriptografar", e);
        }
    }
}
```

O nonce (aleatório, único por criptografia) vai junto do ciphertext. O GCM
também verifica integridade: dado adulterado lança exceção no `doFinal`, você
não decide isso.

## Derivação de chave: a API KDF (JEP 510)

O JDK 25 ganhou API oficial de **Key Derivation Function** (`javax.crypto.KDF`):
derivar várias chaves de um segredo com HKDF, com separação de contexto. Antes
isso era feito na mão (e errado).

```java
import javax.crypto.KDF;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;

KDF hkdf = KDF.getInstance("HKDF-SHA256");

SecretKey aesKey = hkdf.deriveKey("AES",
        HKDFParameterSpec.ofExtract()
                .addIKM(sharedSecret)
                .addSalt(salt)
                .thenExpand("payments-v1-encryption".getBytes(), 32));

byte[] authKey = hkdf.deriveData(
        HKDFParameterSpec.ofExtract()
                .addIKM(sharedSecret)
                .addSalt(salt)
                .thenExpand("payments-v1-auth".getBytes(), 32));
```

O mesmo segredo gera chaves independentes só mudando o contexto (`info`). O
caso de uso: um segredo mestre negociado, de onde saem a chave de cifra, a de
HMAC, a de sessão.

## TLS e HTTPS

O `HttpClient` (módulo 19) usa TLS por padrão em URLs `https`. Coisas que
importam:

- **Não desligue a verificação de certificado** pra "fazer funcionar rápido".
  Um `TrustManager` que aceita tudo vira mitm na certa.
- TLS 1.2 é o mínimo; TLS 1.0/1.1 morreram. O Java moderno já exige.
- Pra se comunicar com serviço de outra empresa (banco, gov), o certificado
  da contraparte é a identidade. Se não dá pra validar, não mande dado
  sensível.

## Deserialização: o buraco clássico

O Java serializa objetos com `ObjectOutputStream` (módulo 15). Desserializar
dado não confiável é vetor de ataque (gadget chains → execução remota). Regras:

- **Não desserialize dado de fora**. JSON (módulo 19) substitui pros dados que
  atravessam fronteira.
- Se precisar desserializar dado interno, use **filtro** (JEP 415, JDK 17):

```java
ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
        "br.com.exemplo.model.*;java.util.*;!*");
ObjectInputStream in = new ObjectInputStream(Files.newInputStream(path));
in.setObjectInputFilter(filter);
```

O filtro só aceita classes de pacotes conhecidos; o resto é bloqueado antes de
instanciar.

## JWT na mão (pra entender, não pra reimplementar)

JWT é um token de autenticação com três partes base64 separadas por ponto:
`header.payload.signature`. O header diz o algoritmo, o payload carrega os
claims (`sub`, `exp`, `role`), e a assinatura garante que ninguém adulterou.

Montar e validar um JWT HS256 na mão com JCA:

```java
public final class Jwt {

    private final Mac mac;

    public Jwt(byte[] secret) {
        try {
            this.mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("falha ao iniciar HMAC", e);
        }
    }

    public String create(String subject, Duration ttl) {
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        long now = Instant.now().getEpochSecond();
        Map<String, Object> payload = Map.of(
                "sub", subject,
                "iat", now,
                "exp", now + ttl.toSeconds());

        String encodedHeader = base64Url(json(header));
        String encodedPayload = base64Url(json(payload));
        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = base64Url(sign(signingInput));

        return signingInput + "." + signature;
    }

    public String verify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("JWT malformado");
        }
        String signingInput = parts[0] + "." + parts[1];
        String expected = base64Url(sign(signingInput));

        if (!MessageDigest.isEqual(expected.getBytes(UTF_8), parts[2].getBytes(UTF_8))) {
            throw new IllegalArgumentException("assinatura inválida");
        }

        JsonNode payload = jsonReader(parts[1]);
        long exp = payload.get("exp").asLong();
        if (Instant.now().getEpochSecond() >= exp) {
            throw new IllegalArgumentException("token expirado");
        }
        return payload.get("sub").asText();
    }

    private byte[] sign(String input) {
        return mac.doFinal(input.getBytes(UTF_8));
    }
}
```

O `verify` compara a assinatura com `MessageDigest.isEqual` (timing-safe) e
checa a expiração. O importante do exemplo:

- A assinatura é a proteção: sem o segredo ninguém forja um token. Se o
  segredo vazar, qualquer um emite.
- `exp` no payload vence o token; o servidor não guarda estado (stateless).
- **Na prática use uma lib** (jjwt, Nimbus JOSE). O exemplo é pra você
  entender o que a lib faz e saber ler um token, não pra reimplementar.
  Lib faz o que esse exemplo faz e ainda trata edge case (alg `none`,
  algoritmo trocado, claims obrigatórios).

## Secrets: onde mora o segredo

Segredo (senha de banco, token de API, segredo do JWT, chave de criptografia)
tem um lugar certo e vários errados:

| Lugar | Veredicto |
| ----- | --------- |
| Hardcoded no código | proibido: vira vazamento no repo |
| `application.properties` versionado | proibido: mesma coisa |
| Variável de ambiente | ok: padrão em deploy moderno |
| Arquivo local fora do repo (`.env`, `secrets.properties`) | ok em dev, com `.gitignore` |
| Secret manager (Vault, AWS Secrets Manager, SOPS) | o padrão pra produção |

```java
public static String databasePassword() {
    return System.getenv().getOrDefault("DB_PASSWORD", "");
}
```

Regras:

- **Nunca** logue o segredo (nem em erro). O valor aparece no stack trace e
  vai pro log.
- Rotacione: segredo trocável periodicamente limita o dano de vazamento.
- O default vazio (como acima) falha cedo se a env não existe, em vez de
  tentar conectar com senha errada e esconder o problema.

## SQL injection na prática

O `PreparedStatement` (módulo 17) resolve injection quando você usa
placeholder. O erro clássico é montar SQL por concatenação:

```java
// VULNERÁVEL: o usuário controla o SQL
String sql = "SELECT * FROM products WHERE name = '" + name + "'";
// name = "'; DROP TABLE products; --"
```

Com placeholder, o valor nunca vira SQL:

```java
String sql = "SELECT * FROM products WHERE name = ?";
try (var stmt = conn.prepareStatement(sql)) {
    stmt.setString(1, name);   // nome é dado, não SQL
    // ...
}
```

O `setString` escapa o valor como dado; a string do usuário com `';` não
quebra a query. Duas regras de bolso:

- **Nunca** concatene valor de fora em SQL. Só placeholder.
- **Nunca** monte nome de tabela/coluna dinamicamente sem allow-list. O
  placeholder não funciona pra identificador; valide contra uma lista fixa.

## Vulnerabilidades que você vê no dia a dia

| Risco | A defesa |
| ----- | -------- |
| SQL injection | `PreparedStatement` com placeholder, nunca concatenação (módulo 17) |
| XXE (XML externo) | Não parseie XML não confiável; se precisar, desative entidades externas |
| Deserialization RCE | JSON + filtro, nunca `ObjectInputStream` com input externo |
| Timing attack | `MessageDigest.isEqual` pra comparar segredo |
| Log de senha/segredo | Nunca logue dado sensível; sanitize em DTO |
| Segredo no repo | env vars / secret manager, nunca hardcoded |
| JWT forjado | segredo forte, assinatura verificada com timing-safe, `exp` checado |
| Dependência velha com CVE | `./gradlew dependencyUpdates`, scan de SBOM em CI |

## Comparação com TypeScript

| Conceito | Java | TypeScript/Node |
| -------- | ---- | --------------- |
| Hash de senha | PBKDF2 na JDK, bcrypt/Argon2 por lib | `bcrypt`, `argon2` (npm) |
| Cifra simétrica | `Cipher` AES-GCM | `crypto.createCipheriv` (AES-GCM) |
| Derivação de chave | `javax.crypto.KDF` (JDK 25) | `crypto.hkdf` / `scrypt` |
| Aleatoriedade | `SecureRandom` | `crypto.randomBytes` |
| Timing-safe | `MessageDigest.isEqual` | `crypto.timingSafeEqual` |
| Desserialização | filtro `ObjectInputFilter` | `JSON.parse` (sem código) |
| JWT | lib (jjwt, Nimbus) | lib (`jsonwebtoken`, `jose`) |
| SQL injection | `PreparedStatement` | driver prepara (`pg` params) |
| Secrets | env / secret manager | env / secret manager (igual) |

Os dois têm as mesmas primitivas; o Java expõe criptografia pela JCA (provider
architecture) e o Node pelo módulo `crypto`. A diferença de cultura: no Java a
serialização nativa é perigosa e evitada; no Node não existe equivalente, o
JSON é o único formato.

## O que mudou entre versões

| Feature | Versão | Situação |
| ------- | ------ | -------- |
| `SecurityManager` removido | JDK 24 | Removido |
| Filtros de desserialização (JEP 415) | JDK 17 | Permanente |
| API KDF (JEP 510) | JDK 25 | Permanente |
| PEM encodings (JEP 470) | JDK 25 | Preview |
| Argon2 na JDK | — | Draft de JEP (fora da JDK) |

## Exercícios

1. Implemente um `PasswordHasher` com PBKDF2 (salt por usuário, 600.000
   iterações). Teste: hash de senha igual gera hash diferente (salt), senha
   correta valida, senha errada não valida, e a comparação é timing-safe.
2. Escreva um teste que prova que o salt é aleatório: dois hashes da mesma
   senha não são iguais, e o salt recuperado do hash tem 16 bytes.
3. Implemente AES-GCM e teste o roundtrip: criptografa e descriptografa
   volta igual. Depois adultere um byte do ciphertext e prove que o `decrypt`
   lança exceção (o GCM detecta).
4. Use a API KDF do JDK 25 pra derivar duas chaves (AES e HMAC) do mesmo
   segredo só mudando o `info`. Teste que as chaves são diferentes e que a
   mesma entrada produz a mesma chave (determinismo).
5. Escreva um `AesGcm` que guarda nonce + ciphertext no formato
   `[nonce][ciphertext]` e teste a fronteira: dados vazios, nonce curto
   (deve lançar), e descriptografia de dado corrompido.
6. Implemente um `Jwt` (HS256) como o do módulo e teste: token válido
   devolve o `sub`, token adulterado (mude um char do payload) lança,
   token expirado lança, e dois tokens do mesmo `sub` são diferentes (iat).
7. Escreva um `Jwt` e teste que a verificação é timing-safe: compare a
   assinatura de dois tokens que diferem só no último byte e confirme que o
   tempo não vaza a posição da diferença.
8. Escreva um teste que prova o SQL injection: monte a query vulnerável com
   `name = "'; DROP TABLE products; --"` e mostre que o `PreparedStatement`
   trata como dado (a query não quebra) enquanto a concatenação quebraria.

## Referências

- [Java Cryptography Architecture (Oracle)](https://docs.oracle.com/en/java/javase/25/security/java-cryptography-architecture-jca-reference-guide.html) — `SecureRandom`, `MessageDigest`, `Cipher`, providers
- [Password Storage Cheat Sheet (OWASP)](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) — Argon2id, bcrypt, PBKDF2 e parâmetros mínimos
- [JEP 510 — Key Derivation Function API](https://openjdk.org/jeps/510) — a API `javax.crypto.KDF` (JDK 25)
- [JEP 415 — Deserialization Filters](https://openjdk.org/jeps/415) — `ObjectInputFilter` (JDK 17)
- [JEP 470 — PEM Encodings of Cryptographic Objects (Preview)](https://openjdk.org/jeps/470) — leitura/escrita de PEM (JDK 25)
- [Java SecureRandom (Java API docs)](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureRandom.html) — o gerador de aleatoriedade segura
- [JWT.io](https://jwt.io/) — decodificador e a anatomia do token
- [JSON Web Token Best Practices (OWASP)](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html) — como validar JWT com segurança
- [SQL Injection Prevention (OWASP)](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html) — por que o placeholder resolve

## Próximo módulo

**Evolução do Java e Previews** — o ritmo de release de 6 meses, as previews, e
pra onde a linguagem está indo.

[→ 25 — Evolução do Java e Previews](./25-evolucao-do-java.md)