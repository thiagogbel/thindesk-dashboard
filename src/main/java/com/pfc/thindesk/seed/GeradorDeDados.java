package com.pfc.thindesk.seed;

import com.pfc.thindesk.entity.Chamado;
import com.pfc.thindesk.entity.Cliente;
import com.pfc.thindesk.entity.Prioridade;
import com.pfc.thindesk.entity.StatusChamado;
import com.pfc.thindesk.entity.Tecnico;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bson.types.ObjectId;

/**
 * Gera uma base ficticia, porem realista, para o dashboard:
 *  - volume crescente ao longo dos meses, picos na segunda-feira, pouco movimento no fim de semana;
 *  - horario comercial concentrado entre 8h e 18h;
 *  - tempo de resolucao log-normal, dependente da prioridade, do tipo e do nivel do tecnico;
 *  - status coerente com a idade do chamado (antigos fechados, recentes em aberto).
 * A semente e fixa: rodar duas vezes gera exatamente os mesmos dados.
 */
public class GeradorDeDados {

    static final String[] NOMES = {"Ana", "Bruno", "Carla", "Daniel", "Eduarda", "Felipe", "Gabriela", "Henrique",
        "Isabela", "João", "Karina", "Lucas", "Mariana", "Nicolas", "Olívia", "Pedro", "Rafaela", "Rodrigo",
        "Sofia", "Thiago", "Vanessa", "Vinícius", "Beatriz", "Caio", "Débora", "Enzo", "Fernanda", "Gustavo",
        "Helena", "Igor", "Juliana", "Leonardo", "Larissa", "Matheus", "Natália", "Otávio", "Paula", "Renato",
        "Sabrina", "Tatiane", "Valentina", "William", "Yasmin", "André", "Bianca", "Cláudio", "Letícia", "Marcelo",
        "Priscila", "Samuel", "Alessandra", "Fábio", "Giovana", "Murilo", "Patrícia", "Ricardo", "Tânia", "Vitor"};
    static final String[] SOBRENOMES = {"Silva", "Santos", "Oliveira", "Souza", "Rodrigues", "Ferreira", "Alves",
        "Pereira", "Lima", "Gomes", "Costa", "Ribeiro", "Martins", "Carvalho", "Almeida", "Lopes", "Soares",
        "Fernandes", "Vieira", "Barbosa", "Rocha", "Dias", "Nascimento", "Andrade", "Moreira", "Nunes", "Marques",
        "Machado", "Mendes", "Freitas", "Cardoso", "Ramos", "Gonçalves", "Santana", "Teixeira", "Araújo", "Pinto",
        "Correia", "Moura", "Cavalcanti", "Azevedo", "Batista", "Campos", "Duarte", "Fonseca", "Horas"};
    static final String[] SETORES = {"Financeiro", "Recursos Humanos", "Comercial", "Marketing", "Logística",
        "Jurídico", "Compras", "Atendimento ao Cliente", "Diretoria", "Produção", "Contabilidade", "TI Interna"};
    static final double[] PESO_SETORES = {12, 8, 16, 7, 11, 4, 6, 14, 3, 10, 6, 3};
    static final String[] UNIDADES = {"São Paulo - Matriz", "Campinas", "Rio de Janeiro", "Belo Horizonte",
        "Curitiba", "Porto Alegre", "Recife"};
    static final double[] PESO_UNIDADES = {35, 15, 14, 12, 10, 8, 6};

    static final String[] TIPOS = {"Acesso e senha", "Hardware", "Software", "Rede e conectividade", "E-mail",
        "Impressora", "Sistema ERP", "Instalação", "Telefonia", "Segurança da informação"};
    static final double[] PESO_TIPOS = {18, 14, 14, 12, 9, 9, 8, 6, 5, 5};
    /** Multiplicador do tempo de resolucao por tipo. */
    static final double[] FATOR_TIPO = {0.35, 1.6, 1.0, 1.2, 0.7, 0.9, 1.4, 1.1, 1.0, 1.3};
    /** Especialidade preferencial para cada tipo. */
    static final String[] ESPECIALIDADE_DO_TIPO = {"Suporte N1", "Infraestrutura", "Sistemas", "Redes", "Suporte N1",
        "Infraestrutura", "Sistemas", "Sistemas", "Redes", "Segurança"};
    static final String[] ESPECIALIDADES = {"Suporte N1", "Infraestrutura", "Redes", "Sistemas", "Segurança", "Banco de Dados"};
    static final double[] PESO_ESPECIALIDADES = {30, 22, 16, 20, 7, 5};

    static final String[][] TITULOS = {
        {"Senha expirada no Windows", "Usuário bloqueado no AD", "Reset de senha do sistema", "Sem acesso à pasta de rede", "Liberação de acesso ao Teams"},
        {"Notebook não liga", "Monitor piscando", "Teclado com teclas falhando", "Computador muito lento", "Troca de mouse"},
        {"Excel travando ao abrir planilha", "Erro ao abrir PDF", "Atualização do Office", "Navegador fechando sozinho", "Licença do software expirada"},
        {"Sem internet na estação", "Wi-Fi caindo na sala de reunião", "VPN não conecta", "Lentidão na rede", "Ponto de rede sem sinal"},
        {"Caixa de e-mail cheia", "E-mail não sincroniza no celular", "Mensagens indo para spam", "Criar lista de distribuição", "Erro ao enviar anexo"},
        {"Impressora não imprime", "Papel atolado", "Instalar impressora de rede", "Impressão saindo borrada", "Scanner não envia para e-mail"},
        {"Erro ao emitir nota fiscal", "Relatório do ERP não carrega", "Acesso ao módulo financeiro", "Integração ERP x estoque falhando", "Lentidão no fechamento do mês"},
        {"Instalar software de desenho", "Configurar novo colaborador", "Instalar certificado digital", "Preparar notebook para viagem", "Instalar driver de leitor"},
        {"Ramal sem tom de discagem", "Configurar softphone", "Headset sem áudio", "Transferência de ramal", "Celular corporativo sem sinal"},
        {"E-mail suspeito de phishing", "Antivírus desatualizado", "Bloqueio de site indevido", "Pendrive com vírus", "Solicitação de liberação de firewall"}};

    static final String[][] SOLUCOES = {
        {"Senha redefinida e política de expiração explicada ao usuário.", "Conta desbloqueada no Active Directory após 5 tentativas inválidas.", "Permissão adicionada ao grupo de segurança do setor."},
        {"Fonte substituída; equipamento testado por 30 minutos.", "Memória RAM ampliada para 16 GB.", "Cabo de vídeo trocado; problema era mau contato.", "Equipamento enviado para garantia do fabricante."},
        {"Reinstalação do pacote Office e limpeza de cache.", "Atualização aplicada e complemento incompatível removido.", "Licença renovada no portal do fornecedor."},
        {"Switch da sala reiniciado e porta reconfigurada.", "Perfil de VPN recriado; certificado estava expirado.", "Access point substituído no 2º andar.", "Cabo de rede refeito (conector danificado)."},
        {"Arquivamento automático configurado para mensagens antigas.", "Conta removida e adicionada novamente no celular.", "Regra de spam ajustada no gateway de e-mail."},
        {"Driver reinstalado e fila de impressão limpa.", "Rolo de tração substituído.", "Toner trocado e cabeçote limpo."},
        {"Chamado escalado ao fornecedor do ERP; correção aplicada em produção.", "Índice do banco recriado; relatório voltou a carregar em 5s.", "Perfil de acesso ajustado pelo administrador do ERP."},
        {"Software instalado conforme catálogo homologado.", "Estação preparada com imagem padrão e acessos liberados.", "Certificado A1 instalado e testado no portal da Receita."},
        {"Ramal reconfigurado na central PABX.", "Softphone instalado e credenciais SIP atualizadas.", "Headset substituído."},
        {"E-mail bloqueado na quarentena e usuário orientado.", "Varredura completa executada; nenhuma ameaça ativa.", "Regra de firewall criada após aprovação da segurança."}};

    static final String[] CANAIS = {"Portal", "E-mail", "Telefone", "Presencial"};
    static final double[] PESO_CANAIS = {45, 25, 20, 10};

    private final Random rnd;
    private final ZoneId zona;

    public GeradorDeDados(long semente, ZoneId zona) {
        this.rnd = new Random(semente);
        this.zona = zona;
    }

    /* ------------------------------------------------------------------ */

    public List<Tecnico> tecnicos(int quantidade) {
        List<Tecnico> lista = new ArrayList<>();
        Set<String> usados = new HashSet<>();
        for (int i = 0; i < quantidade; i++) {
            String nome = nomeUnico(usados);
            Tecnico t = new Tecnico();
            t.setId(new ObjectId().toHexString());
            t.setNome(nome);
            t.setEmail(slug(nome) + "@thindesk.com.br");
            t.setEspecialidade(i < ESPECIALIDADES.length ? ESPECIALIDADES[i] : sorteia(ESPECIALIDADES, PESO_ESPECIALIDADES));
            double n = rnd.nextDouble();
            t.setNivel(n < 0.5 ? "N1" : n < 0.85 ? "N2" : "N3");
            t.setAtivo(i < quantidade - 2); // dois tecnicos desligados (historico permanece)
            lista.add(t);
        }
        return lista;
    }

    public List<Cliente> clientes(int quantidade, Instant agora) {
        List<Cliente> lista = new ArrayList<>();
        Set<String> emails = new HashSet<>();
        for (int i = 0; i < quantidade; i++) {
            String nome = NOMES[rnd.nextInt(NOMES.length)] + " " + SOBRENOMES[rnd.nextInt(SOBRENOMES.length)]
                    + (rnd.nextDouble() < 0.4 ? " " + SOBRENOMES[rnd.nextInt(SOBRENOMES.length)] : "");
            String base = slug(nome);
            String email = base + "@empresa.com.br";
            for (int n = 2; emails.contains(email); n++) {
                email = base + n + "@empresa.com.br";
            }
            emails.add(email);
            Cliente c = new Cliente();
            c.setId(new ObjectId().toHexString());
            c.setNome(nome);
            c.setEmail(email);
            c.setTelefone(String.format("(%02d) 9%04d-%04d", new int[] {11, 19, 21, 31, 41, 51, 81}[rnd.nextInt(7)],
                    rnd.nextInt(10000), rnd.nextInt(10000)));
            c.setSetor(sorteia(SETORES, PESO_SETORES));
            c.setUnidade(sorteia(UNIDADES, PESO_UNIDADES));
            c.setDataCadastro(agora.minus(Duration.ofDays(30 + rnd.nextInt(900))));
            lista.add(c);
        }
        return lista;
    }

    /* ------------------------------------------------------------------ */

    public List<Chamado> chamados(int quantidade, int meses, List<Cliente> clientes, List<Tecnico> tecnicos, Instant agora) {
        LocalDate hoje = agora.atZone(zona).toLocalDate();
        LocalDate inicio = hoje.minusMonths(meses);
        int dias = (int) ChronoUnit.DAYS.between(inicio, hoje) + 1;

        // 1) peso de cada dia: tendencia de crescimento x dia da semana x sazonalidade x ruido
        double[] acumulado = new double[dias];
        double soma = 0;
        for (int i = 0; i < dias; i++) {
            LocalDate d = inicio.plusDays(i);
            double tendencia = 0.7 + 0.6 * i / dias;
            double semana = switch (d.getDayOfWeek()) {
                case MONDAY -> 1.35;
                case TUESDAY -> 1.15;
                case WEDNESDAY -> 1.05;
                case THURSDAY -> 1.0;
                case FRIDAY -> 0.85;
                case SATURDAY -> 0.18;
                case SUNDAY -> 0.08;
            };
            double sazonal = switch (d.getMonthValue()) {
                case 1 -> 0.8;    // ferias
                case 3, 8 -> 1.15; // retomada / auditoria
                case 12 -> 0.85;
                default -> 1.0;
            };
            double ruido = 0.85 + rnd.nextDouble() * 0.3;
            soma += tendencia * semana * sazonal * ruido;
            acumulado[i] = soma;
        }

        Map<String, List<Tecnico>> porEspecialidade = new java.util.HashMap<>();
        List<Tecnico> ativos = tecnicos.stream().filter(Tecnico::isAtivo).toList();
        for (Tecnico t : tecnicos) {
            porEspecialidade.computeIfAbsent(t.getEspecialidade(), k -> new ArrayList<>()).add(t);
        }

        List<Chamado> lista = new ArrayList<>(quantidade);
        for (int n = 0; n < quantidade; n++) {
            // 2) sorteia o dia proporcional ao peso e a hora em horario comercial
            int idx = buscaBinaria(acumulado, rnd.nextDouble() * soma);
            LocalDate dia = inicio.plusDays(idx);
            int hora = horaDoDia(dia.getDayOfWeek());
            ZonedDateTime abertura = dia.atTime(hora, rnd.nextInt(60), rnd.nextInt(60)).atZone(zona);
            Instant dataAbertura = abertura.toInstant();
            if (dataAbertura.isAfter(agora)) {
                dataAbertura = agora.minus(Duration.ofMinutes(5 + rnd.nextInt(240)));
            }

            int tipoIdx = indice(PESO_TIPOS);
            Prioridade prioridade = sorteiaPrioridade(tipoIdx);
            Cliente cliente = clientes.get((int) Math.floor(Math.pow(rnd.nextDouble(), 1.6) * clientes.size()));

            Chamado c = new Chamado();
            c.setId(new ObjectId().toHexString());
            c.setTipo(TIPOS[tipoIdx]);
            c.setTitulo(TITULOS[tipoIdx][rnd.nextInt(TITULOS[tipoIdx].length)]);
            c.setDescricao("Solicitação registrada por " + cliente.getNome() + " (" + cliente.getSetor() + " - "
                    + cliente.getUnidade() + "): " + c.getTitulo().toLowerCase() + ".");
            c.setPrioridade(prioridade);
            c.setCanal(sorteia(CANAIS, PESO_CANAIS));
            c.setClienteId(cliente.getId());
            c.setUsuario(cliente.getNome());
            c.setSetor(cliente.getSetor());
            c.setDataAbertura(dataAbertura);
            c.setPrazoSla(dataAbertura.plus(Duration.ofHours(prioridade.getSlaHoras())));

            // 3) tecnico: preferencialmente da especialidade do tipo
            Tecnico tecnico = escolheTecnico(porEspecialidade.get(ESPECIALIDADE_DO_TIPO[tipoIdx]), ativos, tecnicos, dataAbertura, agora);

            // 4) tempos: primeiro atendimento e resolucao
            double fatorNivel = switch (tecnico.getNivel()) {
                case "N3" -> 0.75;
                case "N2" -> 0.95;
                default -> 1.2;
            };
            double primeiraRespostaMin = logNormal(Math.max(10, prioridade.getSlaHoras() * 6.0), 0.9);
            double resolucaoMin = logNormal(medianaHoras(prioridade) * 60 * FATOR_TIPO[tipoIdx] * fatorNivel, 0.85);
            resolucaoMin = Math.max(resolucaoMin, primeiraRespostaMin + 5);

            Instant primeiro = dataAbertura.plus(Duration.ofMinutes((long) primeiraRespostaMin));
            Instant solucao = dataAbertura.plus(Duration.ofMinutes((long) resolucaoMin));
            boolean travado = rnd.nextDouble() < 0.012; // backlog antigo esquecido (gera "atrasados")
            boolean cancelado = rnd.nextDouble() < 0.035;

            if (cancelado) {
                c.setStatus(StatusChamado.CANCELADO);
                Instant quando = dataAbertura.plus(Duration.ofMinutes(30 + rnd.nextInt(2880)));
                c.setDataFechamento(quando.isAfter(agora) ? agora : quando);
                c.setConsideracaoTecnica(rnd.nextBoolean() ? "Cancelado a pedido do solicitante." : "Chamado duplicado.");
                if (primeiro.isBefore(agora) && rnd.nextBoolean()) {
                    atribui(c, tecnico);
                    c.setDataPrimeiroAtendimento(primeiro.isAfter(c.getDataFechamento()) ? c.getDataFechamento() : primeiro);
                }
            } else if (!travado && solucao.isBefore(agora)) {
                atribui(c, tecnico);
                c.setDataPrimeiroAtendimento(primeiro);
                c.setDataFechamento(solucao);
                c.setTempoResolucaoMin(Duration.between(dataAbertura, solucao).toMinutes());
                c.setSlaCumprido(!solucao.isAfter(c.getPrazoSla()));
                c.setConsideracaoTecnica(SOLUCOES[tipoIdx][rnd.nextInt(SOLUCOES[tipoIdx].length)]);
                boolean confirmado = Duration.between(solucao, agora).toHours() > 48 ? rnd.nextDouble() < 0.92 : rnd.nextDouble() < 0.3;
                c.setStatus(confirmado ? StatusChamado.FECHADO : StatusChamado.RESOLVIDO);
                if (confirmado && rnd.nextDouble() < 0.68) {
                    c.setAvaliacao(avaliacao(Boolean.TRUE.equals(c.getSlaCumprido())));
                }
            } else if (primeiro.isAfter(agora) && !travado) {
                c.setStatus(StatusChamado.ABERTO); // ainda na fila, sem tecnico
            } else {
                atribui(c, tecnico);
                c.setDataPrimeiroAtendimento(primeiro.isAfter(agora) ? agora : primeiro);
                c.setStatus(rnd.nextDouble() < 0.72 ? StatusChamado.EM_ANDAMENTO : StatusChamado.AGUARDANDO_CLIENTE);
                if (rnd.nextDouble() < 0.5) {
                    c.setConsideracaoTecnica("Em análise: " + SOLUCOES[tipoIdx][0].toLowerCase());
                }
            }
            Instant ultima = c.getDataFechamento() != null ? c.getDataFechamento()
                    : c.getDataPrimeiroAtendimento() != null ? c.getDataPrimeiroAtendimento() : dataAbertura;
            c.setUltimaAtualizacao(ultima);
            lista.add(c);
        }

        // 5) protocolo sequencial na ordem de abertura
        lista.sort(Comparator.comparing(Chamado::getDataAbertura));
        for (int i = 0; i < lista.size(); i++) {
            Chamado c = lista.get(i);
            c.setProtocolo(String.format("TD-%d-%06d", c.getDataAbertura().atZone(zona).getYear(), i + 1));
        }
        return lista;
    }

    /* ------------------------------------------------------------------ */

    private Tecnico escolheTecnico(List<Tecnico> especialistas, List<Tecnico> ativos, List<Tecnico> todos,
                                   Instant abertura, Instant agora) {
        boolean recente = Duration.between(abertura, agora).toDays() < 120;
        List<Tecnico> base = rnd.nextDouble() < 0.7 && especialistas != null && !especialistas.isEmpty()
                ? especialistas : todos;
        Tecnico t = base.get(rnd.nextInt(base.size()));
        if (recente && !t.isAtivo()) { // desligados nao recebem chamados recentes
            t = ativos.get(rnd.nextInt(ativos.size()));
        }
        return t;
    }

    private static void atribui(Chamado c, Tecnico t) {
        c.setTecnicoId(t.getId());
        c.setTecnico(t.getNome());
    }

    private int horaDoDia(DayOfWeek dia) {
        if (dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY) {
            return 8 + rnd.nextInt(6);
        }
        double[] pesos = {1, 2, 3, 9, 12, 11, 8, 5, 9, 11, 10, 8, 6, 3, 2, 1}; // 6h..21h
        return 6 + indice(pesos);
    }

    private Prioridade sorteiaPrioridade(int tipoIdx) {
        double[] pesos = tipoIdx == 9 || tipoIdx == 6 ? new double[] {15, 40, 30, 15} : new double[] {32, 46, 16, 6};
        return Prioridade.values()[indice(pesos)];
    }

    private static double medianaHoras(Prioridade p) {
        return switch (p) {
            case CRITICA -> 2.6;
            case ALTA -> 5.5;
            case MEDIA -> 14;
            case BAIXA -> 36;
        };
    }

    private int avaliacao(boolean slaOk) {
        double r = rnd.nextDouble();
        if (slaOk) {
            return r < 0.6 ? 5 : r < 0.88 ? 4 : r < 0.97 ? 3 : 2;
        }
        return r < 0.15 ? 5 : r < 0.45 ? 4 : r < 0.75 ? 3 : r < 0.92 ? 2 : 1;
    }

    private double logNormal(double mediana, double sigma) {
        return mediana * Math.exp(sigma * rnd.nextGaussian());
    }

    private String nomeUnico(Set<String> usados) {
        String nome;
        do {
            nome = NOMES[rnd.nextInt(NOMES.length)] + " " + SOBRENOMES[rnd.nextInt(SOBRENOMES.length)];
        } while (!usados.add(nome));
        return nome;
    }

    private String sorteia(String[] valores, double[] pesos) {
        return valores[indice(pesos)];
    }

    private int indice(double[] pesos) {
        double total = 0;
        for (double p : pesos) {
            total += p;
        }
        double r = rnd.nextDouble() * total;
        for (int i = 0; i < pesos.length; i++) {
            r -= pesos[i];
            if (r < 0) {
                return i;
            }
        }
        return pesos.length - 1;
    }

    private static int buscaBinaria(double[] acumulado, double valor) {
        int lo = 0;
        int hi = acumulado.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (acumulado[mid] < valor) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    static String slug(String nome) {
        String s = java.text.Normalizer.normalize(nome.toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z ]", "")
                .trim()
                .replace(' ', '.');
        return s.isEmpty() ? UUID.randomUUID().toString().substring(0, 8) : s;
    }
}
