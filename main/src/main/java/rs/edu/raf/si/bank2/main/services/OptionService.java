package rs.edu.raf.si.bank2.main.services;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.edu.raf.si.bank2.main.exceptions.*;
import rs.edu.raf.si.bank2.main.models.mariadb.*;
import rs.edu.raf.si.bank2.main.models.mariadb.orders.OptionOrder;
import rs.edu.raf.si.bank2.main.models.mariadb.orders.OrderStatus;
import rs.edu.raf.si.bank2.main.models.mariadb.orders.OrderTradeType;
import rs.edu.raf.si.bank2.main.models.mariadb.orders.OrderType;
import rs.edu.raf.si.bank2.main.repositories.mariadb.*;
import rs.edu.raf.si.bank2.main.services.interfaces.OptionServiceInterface;
import rs.edu.raf.si.bank2.main.services.workerThreads.OptionsRetrieverFromApiWorker;

@Service
public class OptionService implements OptionServiceInterface {

    private final OptionRepository optionRepository;
    private final UserService userService;
    private final StockService stockService;
    private final UserOptionRepository userOptionRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final UserStocksRepository userStocksRepository;
    private final OrderService orderService;
    private final TransactionService transactionService;
    private final BalanceService balanceService;

    private final OptionsRetrieverFromApiWorker optionsRetrieverFromApiWorker;

    @Autowired
    public OptionService(
            OptionRepository optionRepository,
            UserService userService,
            StockService stockService,
            UserOptionRepository userOptionRepository,
            UserRepository userRepository,
            StockRepository stockRepository,
            UserStocksRepository userStocksRepository,
            OrderService orderService,
            TransactionService transactionService,
            BalanceService balanceService) {

        this.optionRepository = optionRepository;
        this.userService = userService;
        this.stockService = stockService;
        this.userOptionRepository = userOptionRepository;
        this.userRepository = userRepository;
        this.stockRepository = stockRepository;
        this.userStocksRepository = userStocksRepository;
        this.orderService = orderService;
        this.transactionService = transactionService;
        this.balanceService = balanceService;
        this.optionsRetrieverFromApiWorker = new OptionsRetrieverFromApiWorker(this);
        this.optionsRetrieverFromApiWorker.start();
    }

    @Override
    public List<Option> findAll() {
        return optionRepository.findAll();
    }

    @Override
    public Option save(Option option) {
        return optionRepository.save(option);
    }

    @Override
    public Optional<Option> findById(Long id) {
        return optionRepository.findById(id);
    }

    @Override
    @Cacheable(value = "optionsStock")
    public List<Option> findByStock(String stockSymbol) {
        //        List<Option> requestedOptions = optionRepository.findAllByStockSymbol(stockSymbol);
        System.out.println("Getting options by stock - fist time (caching into redis)");

        List<Option> requestedOptions = optionRepository.findAllByStockSymbol(stockSymbol.toUpperCase());
        if (requestedOptions.isEmpty()) {
            optionRepository.saveAll(getFromExternalApi(stockSymbol, ""));
        }
        return optionRepository.findAllByStockSymbol(stockSymbol.toUpperCase());
    }

    @Override
    @Cacheable(value = "optionsStockDate")
    public List<Option> findByStockAndDate(String stockSymbol, String regularDate) {
        System.out.println("Getting options for stock and date - fist time (caching into redis)");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        LocalDate date = LocalDate.parse(regularDate, formatter);
        formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        LocalDateTime dateTime = LocalDateTime.parse(regularDate + " 02:00:00", formatter);
        long milliseconds =
                dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

        List<Option> requestedOptions =
                optionRepository.findAllByStockSymbolAndExpirationDate(stockSymbol.toUpperCase(), date);
        if (requestedOptions.isEmpty()) {
            String parsedDate = "" + milliseconds / 1000;
            // todo            optionRepository.deleteAll();  skloneno da ne bi pravilo bugove (vrati ako bude pravilo
            // probleme kasnije)
            optionRepository.saveAll(getFromExternalApi(stockSymbol, parsedDate));
        }
        return optionRepository.findAllByStockSymbolAndExpirationDate(stockSymbol.toUpperCase(), date);
    }

    @Override
    @Transactional
    public void updateAllOptionsInDb() {
        List<Option> freshOptions = this.getFiveMostImportantOptionsFromApi();
        List<Option> newOptions = new ArrayList<>(); // list of freshOptions which don't have corresponding option in db
        for (Option freshOption : freshOptions) {
            Optional<Option> existingOption = this.optionRepository.findByContractSymbolAndStockSymbolAndOptionType(
                    freshOption.getContractSymbol(),
                    freshOption.getStockSymbol(),
                    freshOption
                            .getOptionType()); // search using a combination of these three fields should always yield a
            // unique result
            if (existingOption.isPresent()) {
                this.optionRepository
                        .updateOption( // forwarding all fields as arguments because forwarding whole object was
                                // problematic
                                existingOption.get().getId(),
                                freshOption.getStockSymbol(),
                                freshOption.getContractSymbol(),
                                freshOption.getOptionType(),
                                freshOption.getStrike(),
                                freshOption.getImpliedVolatility(),
                                freshOption.getPrice(),
                                freshOption.getExpirationDate(),
                                freshOption.getOpenInterest(),
                                freshOption.getContractSize(),
                                freshOption.getMaintenanceMargin(),
                                freshOption.getBid(),
                                freshOption.getAsk(),
                                freshOption.getChangePrice(),
                                freshOption.getPercentChange(),
                                freshOption.getInTheMoney());
            } else {
                newOptions.add(freshOption);
            }
        }
        this.optionRepository.saveAll(newOptions);
    }

    @Transactional
    public List<Option> getFiveMostImportantOptionsFromApi() {
        List<Option> appleOptions = this.getFromExternalApi("AAPL", "");
        List<Option> googleOptions = this.getFromExternalApi("GOOGL", "");
        List<Option> amazonOptions = this.getFromExternalApi("AMZN", "");
        List<Option> teslaOptions = this.getFromExternalApi("TSLA", "");
        List<Option> netflixOptions = this.getFromExternalApi("NFLX", "");

        List<Option> optionList = new ArrayList<>();
        optionList.addAll(appleOptions);
        optionList.addAll(googleOptions);
        optionList.addAll(amazonOptions);
        optionList.addAll(teslaOptions);
        optionList.addAll(netflixOptions);
        return optionList;
    }

    public UserStock buyStockUsingOption(Long userOptionId, Long userId)
            throws TooLateToBuyOptionException, OptionNotInTheMoneyException, OptionNotFoundException,
                    StockNotFoundException {

        Integer contractSize = 100;
        Optional<UserOption> userOptionFromDBOptional = userOptionRepository.findById(userOptionId);
        Optional<User> userFromDBOptional = userRepository.findById(userId);

        // Ako user-opcija postoji
        if (userOptionFromDBOptional.isPresent()) {
            UserOption userOptionFromDB = userOptionFromDBOptional.get();
            // Proveri da li je expirationDate prosao
            if (userOptionFromDB.getExpirationDate().isAfter(LocalDate.now())) {

                // Ako nije prosao
                // Pitaj da li je lastPrice veca od strike
                // Ako jeste to znaci da je opcija 'In the Money' i user hoce da kupi taj stock za strike
                // TODO last Price (za sada) ili current price iz Stocka (ako se ispostavi da je to ispravno)
                if (userOptionFromDB.getOption().getPrice() > userOptionFromDB.getStrike()
                        && userFromDBOptional.isPresent()) {
                    // Pronalazi se stock po simbolu
                    Optional<Stock> stockFromDBOptional = stockRepository.findStockBySymbol(
                            userOptionFromDB.getOption().getStockSymbol());
                    if (stockFromDBOptional.isPresent()) {
                        Stock stock = stockFromDBOptional.get();
                        // Kreira se novi user-stock
                        UserStock newUserStock = UserStock.builder()
                                .user(userFromDBOptional.get())
                                .stock(stock)
                                .amount(userOptionFromDB.getAmount() * contractSize)
                                .amountForSale(userOptionFromDB.getAmount() * contractSize)
                                .build();
                        userOptionRepository.deleteById(userOptionId);
                        return userStocksRepository.save(newUserStock);
                        // TODO Update user balance (buy stock by STRIKE price)
                    } else
                        throw new StockNotFoundException(
                                userOptionFromDB.getOption().getStockSymbol());
                } else throw new OptionNotInTheMoneyException(userOptionId);
            } else
                throw new TooLateToBuyOptionException(
                        userOptionFromDB.getOption().getExpirationDate(), userOptionId);
        } else throw new OptionNotFoundException(userOptionId);
    }

    public List<UserOption> getUserOptions(Long userId) {
        return userOptionRepository.getUserOptionsByUserId(userId);
    }

    public void sellStockUsingOption(Long userOptionId, Long userId) {
        // TODO Check if date expired
        // TODO Check if in money
        //        price < strike
        // TODO Sell stock by STRIKE price
    }

    @Transactional
    public UserOption buyOption(Long optionId, Long userId, Integer amount, double premium)
            throws UserNotFoundException, OptionNotFoundException {

        Optional<Option> optionOptional = optionRepository.findById(optionId);
        if (optionOptional.isPresent()) {

            Option optionFromDB = optionOptional.get();

            if (optionFromDB.getOpenInterest() < amount)
                throw new NotEnoughOptionsAvailableException(optionFromDB.getOpenInterest(), amount);

            Optional<User> userOptional = userService.findById(userId);

            if (userOptional.isPresent()) {
                User userFromDB = userOptional.get();

                OptionOrder optionOrder = new OptionOrder(
                        0L,
                        OrderType.OPTION,
                        OrderTradeType.BUY,
                        OrderStatus.IN_PROGRESS,
                        optionFromDB.getStockSymbol(),
                        amount,
                        premium,
                        this.getTimestamp(),
                        userFromDB);
                optionOrder = (OptionOrder) this.orderService.save(optionOrder);
                if (userFromDB.getDailyLimit() == null || userFromDB.getDailyLimit() <= 0)
                    throw new NotEnoughMoneyException();
                if (userFromDB.getDailyLimit() < premium) {
                    this.orderService.updateOrderStatus(optionOrder.getId(), OrderStatus.WAITING);
                    throw new NotEnoughMoneyException();
                }
                Balance balance;
                try {
                    balance = balanceService.findBalanceByUserEmailAndCurrencyCode(userFromDB.getEmail(), "USD");
                } catch (BalanceNotFoundException e) {
                    throw e;
                }
                balanceService.reserveAmount((float) premium, userFromDB.getEmail(), "USD", false);

                UserOption userOption = UserOption.builder()
                        .user(userFromDB)
                        .option(optionFromDB)
                        .amount(amount)
                        .premium(premium)
                        .expirationDate(optionFromDB.getExpirationDate())
                        .strike(optionFromDB.getStrike())
                        .type(optionFromDB.getOptionType())
                        .stockSymbol(optionFromDB.getStockSymbol())
                        .build();

                optionFromDB.setOpenInterest(optionFromDB.getOpenInterest() - amount);
                optionRepository.save(optionFromDB);

                // TODO Smanjiti user balance
                this.orderService.updateOrderStatus(optionOrder.getId(), OrderStatus.COMPLETE);
                Transaction transaction = this.transactionService.createTransaction(
                        optionOrder, balance, (float) premium, (float) optionOrder.getPrice());
                this.transactionService.save(transaction);
                this.balanceService.updateBalance(optionOrder, userFromDB.getEmail(), "USD", false);
                return userOptionRepository.save(userOption);

            } else {
                throw new UserNotFoundException(userId);
            }
        } else {
            throw new OptionNotFoundException(optionId);
        }
    }

    public List<UserOption> getUserOptionsByIdAndStockSymbol(Long userId, String stockSymbol) {
        return userOptionRepository.getUserOptionsByUserIdAndStockSymbol(userId, stockSymbol);
    }

    public UserOption sellOption(Long userOptionId, Double premium) throws OptionNotFoundException {

        Optional<UserOption> userOptionOptional = userOptionRepository.findById(userOptionId);

        if (userOptionOptional.isPresent()) {

            UserOption userOptionFromDB = userOptionOptional.get();

            userOptionFromDB.setPremium(premium);
            userOptionFromDB.setUser(null);

            return userOptionRepository.save(userOptionFromDB);

            // TODO Simulirati prodaju opcije (povecati balance user-a za premium vrednost)
        } else throw new OptionNotFoundException(userOptionId);
    }

    public List<Option> getFromExternalApi(String stockSymbol, String date) {

        String apiUrl;
        List<Option> optionList = new ArrayList<>();

        if (date == null) apiUrl = "https://query1.finance.yahoo.com/v7/finance/options/" + stockSymbol;
        else apiUrl = "https://query1.finance.yahoo.com/v7/finance/options/" + stockSymbol + "?date=" + date;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(apiUrl)).build();
        HttpResponse<String> response;

        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject fullResponse = new JSONObject(response.body());

            JSONObject optionChain = fullResponse.getJSONObject("optionChain");
            JSONArray result = optionChain.getJSONArray("result");

            if (result.length() == 0) {
                return optionList;
            }

            JSONObject object = result.getJSONObject(0);

            JSONArray optionsArray = object.getJSONArray("options");
            JSONObject options = optionsArray.getJSONObject(0);

            JSONArray callsArray = options.getJSONArray("calls");

            for (int i = 0; i < callsArray.length(); i++) {

                Object o = callsArray.get(i);

                JSONObject json = (JSONObject) o;
                //                System.out.println(json);
                //                System.out.println(json.length() + " ovo je velicina");
                //                System.out.println(json.getDouble("change") + " bidovi ");

                Integer contractSize = 100;
                Double price = json.getDouble("lastPrice");

                Double maintenanceMargin = contractSize * 0.5 * price;

                Option newOption = Option.builder()
                        .contractSymbol(json.getString("contractSymbol"))
                        .stockSymbol(stockSymbol.toUpperCase())
                        .optionType("CALL")
                        .strike(json.getDouble("strike"))
                        .impliedVolatility(json.getDouble("impliedVolatility"))
                        .expirationDate(Instant.ofEpochMilli(json.getInt("expiration") * 1000L)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate())
                        .openInterest(json.has("openInterest") ? json.getInt("openInterest") : 0)
                        .contractSize(contractSize)
                        .price(price)
                        .maintenanceMargin(maintenanceMargin)
                        .bid(json.getDouble("bid"))
                        .ask(json.getDouble("ask"))
                        .changePrice(json.getDouble("change"))
                        .percentChange(json.has("percentChange") ? json.getDouble("percentChange") : 0.0)
                        .inTheMoney(json.getBoolean("inTheMoney"))
                        .build();

                optionList.add(newOption);
            }

            JSONArray putsArray = options.getJSONArray("puts");

            for (int i = 0; i < callsArray.length(); i++) {

                Object o = callsArray.get(i);

                JSONObject json = (JSONObject) o;

                Integer contractSize = 100;
                Double price = json.getDouble("lastPrice");

                Double maintenanceMargin = contractSize * 0.5 * price;

                Option newOption = Option.builder()
                        .contractSymbol(json.getString("contractSymbol"))
                        .stockSymbol(stockSymbol.toUpperCase())
                        .optionType("PUT")
                        .strike(json.getDouble("strike"))
                        .impliedVolatility(json.getDouble("impliedVolatility"))
                        .expirationDate(Instant.ofEpochMilli(json.getInt("expiration") * 1000L)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate())
                        .openInterest(json.has("openInterest") ? json.getInt("openInterest") : 0)
                        .contractSize(contractSize)
                        .price(price)
                        .maintenanceMargin(maintenanceMargin)
                        .bid(json.getDouble("bid"))
                        .ask(json.getDouble("ask"))
                        .changePrice(json.getDouble("change"))
                        .percentChange(json.has("percentChange") ? json.getDouble("percentChange") : 0.0)
                        .inTheMoney(json.getBoolean("inTheMoney"))
                        .build();

                optionList.add(newOption);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

//        System.err.println(optionList.size());

        return optionList;
    }

    private String getTimestamp() {
        LocalDateTime currentDateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return currentDateTime.format(formatter);
    }
}
