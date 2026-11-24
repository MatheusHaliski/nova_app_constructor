package com.nova.constructor.generator;

import com.nova.constructor.generator.GenerationPlan.Artifact;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** Estrutura inicial de um backend NestJS (TypeScript). */
@Component
public class NestJsGenerator implements StackGenerator {

    @Override
    public String id() {
        return "nestjs";
    }

    @Override
    public void generate(GenerationPlan plan, String root, Map<String, String> files) {
        ProjectSpec spec = plan.spec();
        List<String> packages = spec.backend().packages();
        boolean config = packages.contains("config");
        boolean typeorm = packages.contains("typeorm") && spec.database() != null;
        boolean swagger = packages.contains("swagger");
        boolean validation = packages.contains("validation");

        files.put(root + "package.json", packageJson(plan, Map.of(
                "build", "nest build", "start", "node dist/main.js", "start:dev", "nest start --watch",
                "test", "tsc --noEmit")));
        files.put(root + "tsconfig.json", """
                {
                  "compilerOptions": {
                    "module": "commonjs",
                    "target": "ES2023",
                    "outDir": "./dist",
                    "rootDir": "./src",
                    "strict": true,
                    "experimentalDecorators": true,
                    "emitDecoratorMetadata": true,
                    "esModuleInterop": true,
                    "skipLibCheck": true,
                    "sourceMap": true
                  },
                  "include": ["src/**/*"]
                }
                """);
        files.put(root + "nest-cli.json", "{\n  \"sourceRoot\": \"src\"\n}\n");

        List<String> imports = new ArrayList<>();
        List<String> moduleImports = new ArrayList<>();
        imports.add("import { Module } from '@nestjs/common';");
        if (config) {
            imports.add("import { ConfigModule } from '@nestjs/config';");
            moduleImports.add("ConfigModule.forRoot({ isGlobal: true })");
        }
        if (typeorm) {
            imports.add("import { TypeOrmModule } from '@nestjs/typeorm';");
            moduleImports.add("TypeOrmModule.forRoot({\n      type: '" + ("mysql".equals(spec.database().id()) ? "mysql" : "postgres")
                    + "',\n      url: process.env.DATABASE_URL,\n      username: process.env.DATABASE_USER,\n      password: process.env.DATABASE_PASSWORD,\n      autoLoadEntities: true,\n      synchronize: false,\n    })");
        }
        boolean auth = spec.hasFeature("auth");
        if (auth) {
            imports.add("import { AuthModule } from './auth/auth.module';");
            moduleImports.add("AuthModule");
            files.put(root + "src/auth/public.decorator.ts", PUBLIC_DECORATOR);
            files.put(root + "src/auth/jwt-auth.guard.ts", JWT_GUARD);
            files.put(root + "src/auth/auth.controller.ts", AUTH_CONTROLLER);
            files.put(root + "src/auth/auth.module.ts", AUTH_MODULE);
        }
        imports.add("import { StatusController } from './status.controller';");
        files.put(root + "src/app.module.ts", String.join("\n", imports) + "\n\n@Module({\n  imports: ["
                + (moduleImports.isEmpty() ? "" : "\n    " + String.join(",\n    ", moduleImports) + ",\n  ") + "],\n  controllers: [StatusController],\n})\nexport class AppModule {}\n");

        files.put(root + "src/status.controller.ts", Tpl.fill("""
                import { Controller, Get } from '@nestjs/common';
                {{publicImport}}
                /** Endpoint público de verificação: confirma que a API está no ar. */
                {{publicDecorator}}@Controller('api/status')
                export class StatusController {
                  @Get()
                  status() {
                    return { app: {{name}}, status: 'ok' };
                  }
                }
                """, Map.of("name", "'" + spec.name().replace("\\", "\\\\").replace("'", "\\'") + "'",
                "publicImport", auth ? "import { Public } from './auth/public.decorator';\n" : "",
                "publicDecorator", auth ? "@Public()\n" : "")));

        StringBuilder main = new StringBuilder("import 'reflect-metadata';\nimport { NestFactory } from '@nestjs/core';\n");
        if (validation) {
            main.append("import { ValidationPipe } from '@nestjs/common';\n");
        }
        if (swagger) {
            main.append("import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';\n");
        }
        main.append("import { AppModule } from './app.module';\n\nasync function bootstrap() {\n  const app = await NestFactory.create(AppModule);\n");
        if (validation) {
            main.append("  app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }));\n");
        }
        if (swagger) {
            main.append("  const document = SwaggerModule.createDocument(app, new DocumentBuilder().setTitle(")
                    .append(Tpl.jsonString(spec.name())).append(").setVersion('0.1.0').build());\n  SwaggerModule.setup('docs', app, document);\n");
        }
        main.append("  await app.listen(process.env.PORT ?? 8080);\n}\n\nbootstrap();\n");
        files.put(root + "src/main.ts", main.toString());

        if (spec.ai() != null && "anthropic".equals(spec.ai().provider())) {
            files.put(root + "src/ai/ai.service.ts", Tpl.fill("""
                    import Anthropic from '@anthropic-ai/sdk';
                    import { Injectable } from '@nestjs/common';

                    /** IA externa do app. A chave vem de ANTHROPIC_API_KEY. */
                    @Injectable()
                    export class AiService {
                      private readonly client = new Anthropic();

                      async ask(prompt: string): Promise<string> {
                        const response = await this.client.messages.create({
                          model: '{{model}}',
                          max_tokens: 16000,
                          messages: [{ role: 'user', content: prompt }],
                        });
                        return response.content.flatMap((block) => (block.type === 'text' ? [block.text] : [])).join('');
                      }
                    }
                    """, Map.of("model", spec.ai().model())));
        }
        if (spec.hasFeature("docker")) {
            files.put(root + "Dockerfile", """
                    FROM node:22-alpine AS build
                    WORKDIR /app
                    COPY package.json ./
                    RUN npm install
                    COPY . .
                    RUN npm run build

                    FROM node:22-alpine
                    WORKDIR /app
                    ENV NODE_ENV=production
                    COPY package.json ./
                    RUN npm install --omit=dev
                    COPY --from=build /app/dist ./dist
                    EXPOSE 8080
                    CMD ["node", "dist/main.js"]
                    """);
        }
    }

    // Autenticação: todas as rotas exigem token, exceto as marcadas com @Public().

    private static final String PUBLIC_DECORATOR = """
            import { SetMetadata } from '@nestjs/common';

            export const IS_PUBLIC = 'isPublic';

            /** Marca uma rota (ou um controller) como pública: não exige token. */
            export const Public = () => SetMetadata(IS_PUBLIC, true);
            """;

    private static final String JWT_GUARD = """
            import { CanActivate, ExecutionContext, Injectable, UnauthorizedException } from '@nestjs/common';
            import { Reflector } from '@nestjs/core';
            import { JwtService } from '@nestjs/jwt';
            import { IS_PUBLIC } from './public.decorator';

            type AuthenticatedRequest = { headers: Record<string, string | undefined>; user?: unknown };

            /** Exige "Authorization: Bearer <token>" válido em todas as rotas que não são @Public(). */
            @Injectable()
            export class JwtAuthGuard implements CanActivate {
              constructor(
                private readonly jwt: JwtService,
                private readonly reflector: Reflector,
              ) {}

              async canActivate(context: ExecutionContext): Promise<boolean> {
                if (this.reflector.getAllAndOverride<boolean>(IS_PUBLIC, [context.getHandler(), context.getClass()])) {
                  return true;
                }
                const request = context.switchToHttp().getRequest<AuthenticatedRequest>();
                const [type, token] = (request.headers.authorization ?? '').split(' ');
                if (type !== 'Bearer' || !token) {
                  throw new UnauthorizedException();
                }
                try {
                  request.user = await this.jwt.verifyAsync(token);
                } catch {
                  throw new UnauthorizedException();
                }
                return true;
              }
            }
            """;

    private static final String AUTH_CONTROLLER = """
            import { Body, Controller, Get, Post, Req, UnauthorizedException } from '@nestjs/common';
            import { JwtService } from '@nestjs/jwt';
            import { createHash, timingSafeEqual } from 'node:crypto';
            import { Public } from './public.decorator';

            /** Comparação em tempo constante. */
            function same(a: string, b: string): boolean {
              return timingSafeEqual(createHash('sha256').update(a).digest(), createHash('sha256').update(b).digest());
            }

            /**
             * Login inicial com o usuário administrador das variáveis APP_ADMIN_USER e APP_ADMIN_PASSWORD.
             * Ponto de partida: troque por cadastro de usuários no banco quando o app evoluir.
             */
            @Controller('api/auth')
            export class AuthController {
              constructor(private readonly jwt: JwtService) {}

              @Public()
              @Post('token')
              async token(@Body() body: { username?: string; password?: string }) {
                const user = process.env.APP_ADMIN_USER;
                const password = process.env.APP_ADMIN_PASSWORD;
                if (!user || !password || !body?.username || !body?.password || !same(body.username, user) || !same(body.password, password)) {
                  throw new UnauthorizedException('Usuário ou senha inválidos.');
                }
                const accessToken = await this.jwt.signAsync({ sub: body.username, scope: 'admin' });
                return { accessToken, tokenType: 'Bearer', expiresIn: 1800 };
              }

              /** Exemplo de rota protegida: devolve os dados do token de quem chamou. */
              @Get('me')
              me(@Req() request: { user?: unknown }) {
                return request.user;
              }
            }
            """;

    private static final String AUTH_MODULE = """
            import { Module } from '@nestjs/common';
            import { APP_GUARD } from '@nestjs/core';
            import { JwtModule } from '@nestjs/jwt';
            import { AuthController } from './auth.controller';
            import { JwtAuthGuard } from './jwt-auth.guard';

            /** Chave de assinatura vinda de JWT_SECRET (mínimo de 32 caracteres). */
            function jwtSecret(): string {
              const secret = process.env.JWT_SECRET ?? '';
              if (secret.length < 32) {
                throw new Error('JWT_SECRET precisa ter pelo menos 32 caracteres.');
              }
              return secret;
            }

            @Module({
              imports: [JwtModule.registerAsync({ useFactory: () => ({ secret: jwtSecret(), signOptions: { expiresIn: 1800 } }) })],
              controllers: [AuthController],
              providers: [{ provide: APP_GUARD, useClass: JwtAuthGuard }],
            })
            export class AuthModule {}
            """;

    /** package.json a partir das coordenadas npm {@code nome@versão} do plano. */
    static String packageJson(GenerationPlan plan, Map<String, String> scripts) {
        return packageJson(plan.slug(), plan.backend().dependencies(), plan.backend().plugins(), scripts, null);
    }

    static String packageJson(String name, List<Artifact> dependencies, List<Artifact> plugins, Map<String, String> scripts, String type) {
        StringBuilder sb = new StringBuilder("{\n  \"name\": \"" + name + "\",\n  \"version\": \"0.1.0\",\n  \"private\": true,\n");
        if (type != null) {
            sb.append("  \"type\": \"").append(type).append("\",\n");
        }
        sb.append("  \"scripts\": {\n").append(entries(new TreeMap<>(scripts))).append("  },\n");
        sb.append("  \"dependencies\": {\n").append(entries(npm(dependencies))).append("  },\n");
        sb.append("  \"devDependencies\": {\n").append(entries(npm(plugins))).append("  }\n}\n");
        return sb.toString();
    }

    private static Map<String, String> npm(List<Artifact> artifacts) {
        Map<String, String> out = new TreeMap<>();
        for (Artifact a : artifacts) {
            int at = a.coordinate().lastIndexOf('@');
            out.put(a.coordinate().substring(0, at), a.coordinate().substring(at + 1));
        }
        return out;
    }

    private static String entries(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, String> e : map.entrySet()) {
            sb.append("    \"").append(e.getKey()).append("\": \"").append(e.getValue()).append("\"").append(++i < map.size() ? ",\n" : "\n");
        }
        return sb.toString();
    }
}
