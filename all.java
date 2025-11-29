import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class FootballPlayer {
    String name;
    int price;
    volatile boolean isSold = false;
    final Lock lock = new ReentrantLock();

    public FootballPlayer(String name, int price) {
        this.name = name;
        this.price = price;
    }

    public boolean tryBuy(FootballClub club) {
        // Пытаемся захватить блокировку игрока без ожидания
        if (lock.tryLock()) {
            try {
                // Двойная проверка после захвата блокировки
                if (!isSold && club.hasEnoughBudget(price)) {
                    // Имитируем время на обработку трансфера
                    try {
                        Thread.sleep(50 + new Random().nextInt(50));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    
                    // Совершаем покупку
                    if (club.spendBudget(price)) {
                        isSold = true;
                        System.out.println("✅ " + club.name + " купил " + name + " за " + price + " млн");
                        return true;
                    }
                }
            } finally {
                lock.unlock();
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return name + " (" + price + " млн)";
    }
}

class FootballClub implements Runnable {
    String name;
    private volatile int budget;
    List<FootballPlayer> market;
    List<FootballPlayer> acquiredPlayers = new ArrayList<>();
    private final Lock budgetLock = new ReentrantLock();

    public FootballClub(String name, int budget, List<FootballPlayer> market) {
        this.name = name;
        this.budget = budget;
        this.market = market;
    }

    public boolean hasEnoughBudget(int amount) {
        return budget >= amount;
    }

    public boolean spendBudget(int amount) {
        budgetLock.lock();
        try {
            if (budget >= amount) {
                budget -= amount;
                return true;
            }
            return false;
        } finally {
            budgetLock.unlock();
        }
    }

    public int getRemainingBudget() {
        return budget;
    }

    @Override
    public void run() {
        System.out.println("🏁 " + name + " начинает покупки с бюджетом " + budget + " млн");
        
        Random random = new Random();
        List<FootballPlayer> shuffledMarket = new ArrayList<>(market);
        Collections.shuffle(shuffledMarket); // Каждый клуб просматривает игроков в случайном порядке
        
        for (FootballPlayer player : shuffledMarket) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            
            if (budget <= 0) {
                System.out.println("💸 " + name + " закончил бюджет");
                break;
            }

            // Пытаемся купить игрока
            if (player.tryBuy(this)) {
                acquiredPlayers.add(player);
            }
            
            // Случайная задержка между попытками покупки
            try {
                Thread.sleep(random.nextInt(100));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Отчет по завершению трансферов
        System.out.println("\n=== " + name + " завершил трансферы ===");
        System.out.println("Купленные игроки (" + acquiredPlayers.size() + "): " + acquiredPlayers);
        System.out.println("Остаток бюджета: " + budget + " млн\n");
    }
}

public class Main {
    public static void main(String[] args) {
        System.out.println("⚽ === НАЧАЛО ТРАНСФЕРНОГО ОКНА ===\n");
        
        // Создаем список игроков
        List<FootballPlayer> players = new ArrayList<>(List.of(
            new FootballPlayer("Лионель Месси", 50),
            new FootballPlayer("Криштиану Роналду", 45),
            new FootballPlayer("Килиан Мбаппе", 180),
            new FootballPlayer("Эрлинг Холанн", 170),
            new FootballPlayer("Винисиус Жуниор", 120),
            new FootballPlayer("Кевин Де Брёйне", 80),
            new FootballPlayer("Мохаммед Салах", 90),
            new FootballPlayer("Роберт Левандовски", 60),
            new FootballPlayer("Неймар", 70),
            new FootballPlayer("Харри Кейн", 100),
            new FootballPlayer("Буффон", 30),
            new FootballPlayer("Зидан", 55)
        ));

        // Создаем клубы
        List<FootballClub> clubs = Arrays.asList(
            new FootballClub("Реал Мадрид", 300, players),
            new FootballClub("Барселона", 200, players),
            new FootballClub("Манчестер Сити", 400, players),
            new FootballClub("ПСЖ", 250, players),
            new FootballClub("Бавария", 180, players),
            new FootballClub("Челси", 150, players),
            new FootballClub("Ювентус", 120, players),
            new FootballClub("Ливерпуль", 220, players)
        );

        // Создаем пул потоков
        ExecutorService executor = Executors.newFixedThreadPool(clubs.size());

        // Запускаем все клубы в отдельных потоках
        for (FootballClub club : clubs) {
            executor.execute(club);
        }

        // Завершаем работу executor
        executor.shutdown();
        
        try {
            // Ждем завершения всех задач (максимум 2 минуты)
            if (!executor.awaitTermination(2, TimeUnit.MINUTES)) {
                System.out.println("⏰ Таймаут! Принудительное завершение...");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.out.println("❌ Прервано ожидание завершения потоков");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("🔒 === ТРАНСФЕРНОЕ ОКНО ЗАКРЫТО ===\n");
        
        // Выводим итоговую статистику
        printFinalStatistics(clubs, players);
    }

    private static void printFinalStatistics(List<FootballClub> clubs, List<FootballPlayer> players) {
        System.out.println("📊 ИТОГОВАЯ СТАТИСТИКА:");
        System.out.println("=====================");
        
        int totalSpent = 0;
        int totalPlayersBought = 0;
        
        for (FootballClub club : clubs) {
            int spent = getInitialBudget(club.name) - club.getRemainingBudget();
            totalSpent += spent;
            totalPlayersBought += club.acquiredPlayers.size();
            
            System.out.printf("%-18s: %d игроков, потрачено: %3d млн, остаток: %3d млн%n",
                    club.name, club.acquiredPlayers.size(), spent, club.getRemainingBudget());
        }
        
        System.out.println("\n👥 Статус игроков:");
        System.out.println("-----------------");
        int soldCount = 0;
        for (FootballPlayer player : players) {
            String status = player.isSold ? "✅ ПРОДАН" : "🟢 СВОБОДЕН";
            System.out.printf("%-20s %s%n", player.name, status);
            if (player.isSold) soldCount++;
        }
        
        System.out.println("\n📈 ОБЩАЯ СТАТИСТИКА:");
        System.out.println("-----------------");
        System.out.println("Всего клубов: " + clubs.size());
        System.out.println("Всего игроков на рынке: " + players.size());
        System.out.println("Продано игроков: " + soldCount);
        System.out.println("Общая сумма трансферов: " + totalSpent + " млн");
        System.out.println("Средняя цена игрока: " + (totalSpent / Math.max(soldCount, 1)) + " млн");
    }
    
    private static int getInitialBudget(String clubName) {
        Map<String, Integer> initialBudgets = Map.of(
            "Реал Мадрид", 300,
            "Барселона", 200,
            "Манчестер Сити", 400,
            "ПСЖ", 250,
            "Бавария", 180,
            "Челси", 150,
            "Ювентус", 120,
            "Ливерпуль", 220
        );
        return initialBudgets.getOrDefault(clubName, 0);
    }
}