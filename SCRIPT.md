1. Запустить контейнеры командой `docker-compose up -d`
1. Создать схему БД командой `php bin/console doctrine:migrations:migrate`
1. Настроить шардирование БД: проверить наличие узлов `SELECT * FROM master_get_active_worker_nodes();`. Создать распределенные таблицы `select create_reference_table('user');  SELECT create_distributed_table('tweet', 'id')`;
1. Послать запрос `Add user` из коллекции Postman, проверить, что возвращается `success: true` и идентификатор
пользователя
1. Создать подписку пользователя на себя (запрос `Subscribe` с телом ```{ "authorId":1, "followerId":1 }``` )
1. Проверить feed для пользователя 1 (`{{host}}/api/v1/tweet/feed?userId=1`)
1. Сгенерировать 1000 твитов `time docker exec -it php bash`: `php app/console tweets:add 1 1000`
1. Добавляем сущность ленты (файл `src/Entity/Feed.php`)
    ```php
    <?php
    declare(strict_types=1);
    
    namespace App\Entity;
    
    use App\Entity\Traits\CreatedAtTrait;
    use App\Entity\Traits\UpdatedAtTrait;
    use Doctrine\ORM\Mapping;
    
    /**
     * @author Mikhail Kamorin aka raptor_MVK
     *
     * @copyright 2020, raptor_MVK
     *
     * @Mapping\Table(
     *     name="feed",
     *     uniqueConstraints={@Mapping\UniqueConstraint(columns={"reader_id"})}
     * )
     * @Mapping\Entity
     * @Mapping\HasLifecycleCallbacks
     */
    class Feed
    {
        use CreatedAtTrait;
        use UpdatedAtTrait;
    
        /**
         * @Mapping\Column(name="id", type="bigint", unique=true)
         * @Mapping\Id
         * @Mapping\GeneratedValue(strategy="IDENTITY")
         */
        private $id;
    
        /**
         * @var User
         *
         * @Mapping\ManyToOne(targetEntity="User")
         * @Mapping\JoinColumns({
         *     @Mapping\JoinColumn(name="reader_id", referencedColumnName="id")
         * })
         */
        private $reader;
    
        /**
         * @var array | null
         *
         * @Mapping\Column(type="json_array", nullable=true)
         */
        private $tweets;
    
        /**
         * @return mixed
         */
        public function getId()
        {
            return $this->id;
        }
    
        /**
         * @param mixed $id
         */
        public function setId($id): void
        {
            $this->id = $id;
        }
    
        /**
         * @return User
         */
        public function getReader(): User
        {
            return $this->reader;
        }
    
        /**
         * @param User $reader
         */
        public function setReader(User $reader): void
        {
            $this->reader = $reader;
        }
    
        /**
         * @return array|null
         */
        public function getTweets(): ?array
        {
            return $this->tweets;
        }
    
        /**
         * @param array|null $tweets
         */
        public function setTweets(?array $tweets): void
        {
            $this->tweets = $tweets;
        }
    }
    ```
1. Создаём миграцию для сущности ленты командой `php bin/console doctrine:migrations:diff`
1. Проверяем, что создался файл с миграцией в `src/Migrations`
1. Накатываем миграцию командой `php bin/console doctrine:migrations:migrate`
1. В файле `src/Entity/Tweet.php` добавляем новый метод:
    ```php
    public function toFeed(): array
    {
        return [
            'id' => $this->id,
            'author' => $this->getAuthor()->getLogin(),
            'text' => $this->text,
            'createdAt' => $this->createdAt->format('Y-m-d h:i:s'),
        ];
    }
    ```
1. Создаём сервис для работы с лентой (файл `src/Entity/Service/FeedService.php`)
    ```php
    <?php
    declare(strict_types=1);
    
    namespace App\Service;
    
    use App\Entity\Feed;
    use App\Entity\Tweet;
    use App\Entity\User;
    use Doctrine\ORM\EntityManagerInterface;
    
    /**
     * @author Mikhail Kamorin aka raptor_MVK
     *
     * @copyright 2020, raptor_MVK
     */
    final class FeedService
    {
        /** @var EntityManagerInterface */
        private $entityManager;
    
        public function __construct(EntityManagerInterface $entityManager)
        {
            $this->entityManager = $entityManager;
        }
    
        public function getFeed(int $userId, int $count): array
        {
            $feed = $this->getFeedFromRepository($userId);
    
            return $feed === null ? [] : array_slice($feed->getTweets(), -$count);
        }
    
        public function putTweet(Tweet $tweet, int $userId): bool
        {
            $feed = $this->getFeedFromRepository($userId);
            if ($feed === null) {
                return false;
            }
            $tweets = $feed->getTweets();
            $tweets[] = $tweet->toFeed();
            $feed->setTweets($tweets);
            $this->entityManager->persist($feed);
            $this->entityManager->flush();
    
            return true;
        }
    
        private function getFeedFromRepository(int $userId): ?Feed
        {
            $userRepository = $this->entityManager->getRepository(User::class);
            $reader = $userRepository->find($userId);
            if (!($reader instanceof User)) {
                return null;
            }
    
            $feedRepository = $this->entityManager->getRepository(Feed::class);
            $feed = $feedRepository->findOneBy(['reader' => $reader]);
            if (!($feed instanceof Feed)) {
                $feed = new Feed();
                $feed->setReader($reader);
                $feed->setTweets([]);
            }
    
            return $feed;
        }
    }
    ```
1. Создаём контроллер для ленты (класс `src\Controller\Api\v1\FeedController.php`)
    ```php
    <?php
    declare(strict_types=1);
    
    namespace App\Controller\Api\v1;
    
    use App\Service\FeedService;
    use FOS\RestBundle\Controller\Annotations;
    use FOS\RestBundle\View\View;
    
    /**
     * @author Mikhail Kamorin aka raptor_MVK
     *
     * @copyright 2020, raptor_MVK
     *
     * @Annotations\Route("/api/v1/feed")
     */
    final class FeedController
    {
        /** @var int */
        private const DEFAULT_FEED_SIZE = 20;
    
        /** @var FeedService */
        private $feedService;
    
        public function __construct(FeedService $feedService)
        {
            $this->feedService = $feedService;
        }
    
        /**
         * @Annotations\Get("")
         *
         * @Annotations\QueryParam(name="userId", requirements="\d+")
         * @Annotations\QueryParam(name="count", requirements="\d+", nullable=true)
         */
        public function getFeedAction(int $userId, ?int $count = null): View
        {
            $count = $count ?? self::DEFAULT_FEED_SIZE;
            $tweets = $this->feedService->getFeed($userId, $count);
            $code = empty($tweets) ? 204 : 200;
    
            return View::create(['tweets' => $tweets], $code);
        }
    }
    ```
1. В файле `src/Service/TweetService.php` делаем:
    1. Добавляем в зависимости `FeedService` и `SubscriptionService`, исправляя конструктор:
        ```php
        /** @var SubscriptionService */
        private $subscriptionService;
        /** @var FeedService */
        private $feedService;
    
        public function __construct(EntityManagerInterface $entityManager, SubscriptionService $subscriptionService, FeedService $feedService)
        {
            $this->entityManager = $entityManager;
            $this->subscriptionService = $subscriptionService;
            $this->feedService = $feedService;
        }
        ```    
    1. Исправляем метод `saveTweet` (меняем модификатор доступа и возвращаемое значение):
        ```php
        private function saveTweet(int $authorId, string $text): ?Tweet
        {
            $tweet = new Tweet();
            $userRepository = $this->entityManager->getRepository(User::class);
            $author = $userRepository->find($authorId);
            if (!($author instanceof User)) {
                return null;
            }
            $tweet->setAuthor($author);
            $tweet->setText($text);
            $this->entityManager->persist($tweet);
            $this->entityManager->flush();
    
            return $tweet;
        }
        ```
    1. Добавляем три новых метода:
        ```php
        public function saveTweetSync(int $authorId, string $text): bool
        {
            $tweet = $this->saveTweet($authorId, $text);
    
            if ($tweet === null) {
                return false;
            }
    
            $this->spreadTweet($tweet);
    
            return true;
        }
    
        public function saveTweetAsync(int $authorId, string $text): bool
        {
            $tweet = $this->saveTweet($authorId, $text);
    
            if ($tweet === null) {
                return false;
            }
    
            // TODO: make async request
    
            return true;
        }   

        private function spreadTweet(Tweet $tweet): void
        {
            $followerIds = $this->subscriptionService->getFollowerIds($tweet->getAuthor()->getId());
    
            foreach ($followerIds as $followerId) {
                $this->feedService->putTweet($tweet, $followerId);
            }
        }
        ```
1. В файле `src/Controller/Api/v1/TweetController.php` исправляем метод `postAction`:
    ```php
    /**
     * @Annotations\Post("")
     *
     * @RequestParam(name="authorId", requirements="\d+")
     * @RequestParam(name="text")
     * @RequestParam(name="async", requirements="0|1", nullable=true)
     */
    public function postTweetAction(int $authorId, string $text, ?int $async): View
    {
        $success = $async === 1 ?
            $this->tweetService->saveTweetAsync($authorId, $text) :
            $this->tweetService->saveTweetSync($authorId, $text);
        $code = $success ? 200 : 400;
    
        return View::create(['success' => $success], $code);
    }
    ```
1. Для проверки синхронного запроса добавим 1000 фолловеров командой `php bin/console followers:add 1 1000`
1. Послать запрос `Post tweet` из коллекции Postman, проверить, сколько времени он выполняется
1. Добавляем описание продюсера и консьюмера в файл `config/packages/old_sound_rabbit_mq.yaml` в секцию
`old_sound_rabbit_mq`
    ```yaml
    producers:
        tweet.published:
            connection: default
            exchange_options: {name: 'old_sound_rabbit_mq.tweet.published', type: direct}
    
    consumers:
        tweet.published:
            connection: default
            exchange_options: {name: 'old_sound_rabbit_mq.tweet.published', type: direct}
            queue_options: {name: 'old_sound_rabbit_mq.consumer.tweet.published'}
            callback: App\Consumer\TweetPublishedConsumer\Consumer
            idle_timeout: 300
            idle_timeout_exit_code: 0
            graceful_max_execution:
                timeout: 1800
                exit_code: 0
            qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    ```
1. В файл `src/Entity/Tweet.php` добавляем метод `toAMQPMessage`
    ```php
    public function toAMPQMessage(): string
    {
        return json_encode(['tweetId' => (int)$this->id], JSON_THROW_ON_ERROR, 512);
    }
    ```
1. В файле `src/Service/TweetService.php` делаем:
    1. Добавляем в зависимости `ProducerInterface`, исправляя конструктор:
        ```php
        /** @var ProducerInterface */
        private $producer;
    
        public function __construct(EntityManagerInterface $entityManager, SubscriptionService $subscriptionService, FeedService $feedService, ProducerInterface $producer)
        {
            $this->entityManager = $entityManager;
            $this->subscriptionService = $subscriptionService;
            $this->feedService = $feedService;
            $this->producer = $producer;
        }
        ```    
    1. Исправляем метод `saveTweetAsync` (добавляем отправку сообщения вместо TODO):
        ```php
        public function saveTweetAsync(int $authorId, string $text): bool
        {
            $tweet = $this->saveTweet($authorId, $text);
    
            if ($tweet === null) {
                return false;
            }
    
            $this->producer->publish($tweet->toAMPQMessage());
    
            return true;
        }
        ```
1. В файл `config/services.yaml` добавляем инъекцию нашего продюсера в сервис `TweetService` в секцию `services`
    ```yaml
    App\Service\TweetService:
        autowire: true
        arguments:
            $producer: "@old_sound_rabbit_mq.tweet.published_producer"
    ```
1. Создаём файл `src/Consumer/TweetPublishedConsumer/Input/Message.php`
    ```php
    <?php
    declare(strict_types=1);
    
    namespace App\Consumer\TweetPublishedConsumer\Input;
    
    use Symfony\Component\Validator\Constraints;
    
    /**
     * @author Mikhail Kamorin aka raptor_MVK
     *
     * @copyright 2020, raptor_MVK
     */
    final class Message
    {
        /**
         * @var int
         *
         * @Constraints\Regex("/^\d+$/")
         */
        private $tweetId;
    
        public static function createFromQueue(string $messageBody): self
        {
            $message = json_decode($messageBody, true, 512, JSON_THROW_ON_ERROR);
            $result = new self();
            $result->tweetId = $message['tweetId'];
    
            return $result;
        }
    
        /**
         * @return int
         */
        public function getTweetId(): int
        {
            return $this->tweetId;
        }
    }
    ``` 
1. Создаём файл `src/Consumer/TweetPublishedConsumer/Consumer.php`
    ```php
    <?php
    declare(strict_types=1);
    
    namespace App\Consumer\TweetPublishedConsumer;
    
    use App\Consumer\TweetPublishedConsumer\Input\Message;
    use App\Entity\Tweet;
    use App\Service\FeedService;
    use App\Service\SubscriptionService;
    use Doctrine\ORM\EntityManagerInterface;
    use JsonException;
    use OldSound\RabbitMqBundle\RabbitMq\ConsumerInterface;
    use PhpAmqpLib\Message\AMQPMessage;
    use Symfony\Component\Validator\Validator\ValidatorInterface;
    
    /**
     * @author Mikhail Kamorin aka raptor_MVK
     *
     * @copyright 2020, raptor_MVK
     */
    final class Consumer implements ConsumerInterface
    {
        /** @var EntityManagerInterface */
        private $entityManager;
        /** @var ValidatorInterface */
        private $validator;
        /** @var SubscriptionService */
        private $subscriptionService;
        /** @var FeedService */
        private $feedService;
    
        public function __construct(EntityManagerInterface $entityManager, ValidatorInterface $validator, SubscriptionService $subscriptionService, FeedService $feedService)
        {
            $this->entityManager = $entityManager;
            $this->validator = $validator;
            $this->subscriptionService = $subscriptionService;
            $this->feedService = $feedService;
        }
    
        public function execute(AMQPMessage $msg): int
        {
            try {
                $message = Message::createFromQueue($msg->getBody());
                $errors = $this->validator->validate($message);
                if ($errors->count() > 0) {
                    return $this->reject((string)$errors);
                }
            } catch (JsonException $e) {
                return $this->reject($e->getMessage());
            }
    
            $tweetRepository = $this->entityManager->getRepository(Tweet::class);
            $tweet = $tweetRepository->find($message->getTweetId());
            if (!($tweet instanceof Tweet)) {
                return $this->reject(sprintf('Tweet ID %s was not found', $message->getTweetId()));
            }
    
            $followerIds = $this->subscriptionService->getFollowerIds($tweet->getAuthor()->getId());
    
            foreach ($followerIds as $followerId) {
                $this->feedService->putTweet($tweet, $followerId);
            }
    
            $this->entityManager->clear();
            $this->entityManager->getConnection()->close();
    
            return self::MSG_ACK;
        }
    
        private function reject(string $error): int
        {
            echo "Incorrect message: $error";
    
            return self::MSG_REJECT;
        }
    }
    ```
1. Заходим в интерфейс админки RabbitMQ по адресу `localhost:15672`, логин / пароль `user` / `password`, видим, что
там пока нет ни точки обмена, ни консьюмера
1. Запускаем консьюмер командой `php bin/console rabbitmq:consumer tweet.published -m 100`
1. Проверяем в админке RabbitMQ, что появилась точка обмена и очередь с одним консьюмером
1. Меняем значение поля `async` в запросе `Post tweet` из коллекции Postman на 1 и проверяем, сколько времени он будет
выполняться
1. Пошлём ещё несколько запросов и посмотрим в админке RabbitMQ на то, как обрабатываются сообщения по времени
1. Зайти в контейнер `rabbit-mq` командой `docker exec -it rabbit-mq sh` и выполнить в нём команду
    ```shell script
    rabbitmq-plugins enable rabbitmq_consistent_hash_exchange
    ```
1. В файл `config/packages/old_sound_rabbit_mq.yaml` добавляем:
    1. В секцию `old_sound_rabbit_mq.producers`
        ```yaml
        update_feed.received:
            connection: default
            exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        ```
    1. В секцию `old_sound_rabbit_mq.consumers`
        ```yaml
        update_feed.received-0:
            connection: default
            exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
            queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-0', routing_key: '1'}
            callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer
            idle_timeout: 300
            idle_timeout_exit_code: 0
            graceful_max_execution:
                timeout: 1800
                exit_code: 0
            qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
        update_feed.received-1:
            connection: default
            exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
            queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-1', routing_key: '1'}
            callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer
            idle_timeout: 300
            idle_timeout_exit_code: 0
            graceful_max_execution:
                timeout: 1800
                exit_code: 0
            qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
        update_feed.received-2:
            connection: default
            exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
            queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-2', routing_key: '1'}
            callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer
            idle_timeout: 300
            idle_timeout_exit_code: 0
            graceful_max_execution:
                timeout: 1800
                exit_code: 0
            qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
        update_feed.received-3:
            connection: default
            exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
            queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-3', routing_key: '1'}
            callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer
            idle_timeout: 300
            idle_timeout_exit_code: 0
            graceful_max_execution:
                timeout: 1800
                exit_code: 0
            qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
        update_feed.received-4:
            connection: default
            exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
            queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-4', routing_key: '1'}
            callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer
            idle_timeout: 300
            idle_timeout_exit_code: 0
            graceful_max_execution:
                timeout: 1800
                exit_code: 0
            qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
        update_feed.received-5:
            connection: default
            exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
            queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-5', routing_key: '1'}
            callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer
            idle_timeout: 300
            idle_timeout_exit_code: 0
            graceful_max_execution:
                timeout: 1800
                exit_code: 0
            qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
        update_feed.received-6:
            connection: default
            exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
            queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-6', routing_key: '1'}
            callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer
            idle_timeout: 300
            idle_timeout_exit_code: 0
            graceful_max_execution:
                timeout: 1800
                exit_code: 0
            qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
        update_feed.received-7:
            connection: default
            exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
            queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-7', routing_key: '1'}
            callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer
            idle_timeout: 300
            idle_timeout_exit_code: 0
            graceful_max_execution:
                timeout: 1800
                exit_code: 0
            qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
        update_feed.received-8:
            connection: default
            exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
            queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-8', routing_key: '1'}
            callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer
            idle_timeout: 300
            idle_timeout_exit_code: 0
            graceful_max_execution:
                timeout: 1800
                exit_code: 0
            qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
        update_feed.received-9:
            connection: default
            exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
            queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-9', routing_key: '1'}
            callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer
            idle_timeout: 300
            idle_timeout_exit_code: 0
            graceful_max_execution:
                timeout: 1800
                exit_code: 0
            qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
       ```
1. Создаём файл `src/Consumer/UpdateFeedReceivedConsumer/Input/Message.php`
    ```php
    <?php
    declare(strict_types=1);
    
    namespace App\Consumer\UpdateFeedReceivedConsumer\Input;
    
    use Symfony\Component\Validator\Constraints;
    
    /**
     * @author Mikhail Kamorin aka raptor_MVK
     *
     * @copyright 2020, raptor_MVK
     */
    final class Message
    {
        /**
         * @var int
         *
         * @Constraints\Regex("/^\d+$/")
         */
        private $tweetId;
    
        /**
         * @var int
         *
         * @Constraints\Regex("/^\d+$/")
         */
        private $recipientId;
    
        public static function createFromQueue(string $messageBody): self
        {
            $message = json_decode($messageBody, true, 512, JSON_THROW_ON_ERROR);
            $result = new self();
            $result->tweetId = $message['tweetId'];
            $result->recipientId = $message['recipientId'];
    
            return $result;
        }
    
        /**
         * @return int
         */
        public function getTweetId(): int
        {
            return $this->tweetId;
        }
    
        /**
         * @return int
         */
        public function getRecipientId(): int
        {
            return $this->recepientId;
        }
    }
    ``` 
1. Создаём файл `src/Consumer/UpdateFeedReceivedConsumer/Consumer.php`
    ```php
    <?php
    declare(strict_types=1);
    
    namespace App\Consumer\UpdateFeedReceivedConsumer;
    
    use App\Consumer\UpdateFeedReceivedConsumer\Input\Message;
    use App\Entity\Tweet;
    use App\Service\FeedService;
    use Doctrine\ORM\EntityManagerInterface;
    use JsonException;
    use OldSound\RabbitMqBundle\RabbitMq\ConsumerInterface;
    use PhpAmqpLib\Message\AMQPMessage;
    use Symfony\Component\Validator\Validator\ValidatorInterface;
    
    /**
     * @author Mikhail Kamorin aka raptor_MVK
     *
     * @copyright 2020, raptor_MVK
     */
    final class Consumer implements ConsumerInterface
    {
        /** @var EntityManagerInterface */
        private $entityManager;
        /** @var ValidatorInterface */
        private $validator;
        /** @var FeedService */
        private $feedService;
    
        public function __construct(EntityManagerInterface $entityManager, ValidatorInterface $validator, FeedService $feedService)
        {
            $this->entityManager = $entityManager;
            $this->validator = $validator;
            $this->feedService = $feedService;
        }
    
        public function execute(AMQPMessage $msg): int
        {
            try {
                $message = Message::createFromQueue($msg->getBody());
                $errors = $this->validator->validate($message);
                if ($errors->count() > 0) {
                    return $this->reject((string)$errors);
                }
            } catch (JsonException $e) {
                return $this->reject($e->getMessage());
            }
    
            $tweetRepository = $this->entityManager->getRepository(Tweet::class);
            $tweet = $tweetRepository->find($message->getTweetId());
            if (!($tweet instanceof Tweet)) {
                return $this->reject(sprintf('Tweet ID %s was not found', $message->getTweetId()));
            }
    
            $this->feedService->putTweet($tweet, $message->getFollowerId());
    
            $this->entityManager->clear();
            $this->entityManager->getConnection()->close();
    
            return self::MSG_ACK;
        }
    
        private function reject(string $error): int
        {
            echo "Incorrect message: $error";
    
            return self::MSG_REJECT;
        }
    }
    ```
1. Добавляем файл `src/Consumer/TweetPublishedConsumer/Output/UpdateFeedMessage.php`
    ```php
    <?php
    declare(strict_types=1);
    
    namespace App\Consumer\TweetPublishedConsumer\Output;
    
    /**
     * @author Mikhail Kamorin aka raptor_MVK
     *
     * @copyright 2020, raptor_MVK
     */
    final class UpdateFeedMessage
    {
        /**
         * @var array
         */
        private $payload;
    
        /**
         * Message constructor.
         * @param array $payload
         */
        public function __construct(int $tweetId, int $followerId)
        {
            $this->payload = ['tweetId' => $tweetId, 'followerId' => $followerId];  
        }
    
        public function toAMQPMessage(): string
        {
            return json_encode($this->payload, JSON_THROW_ON_ERROR, 512);
        }
    }
    ```
1. В файле `src/Consumer/TweetPublishedConsumer/Consumer.php`
    1. Убираем из зависимостей `FeedService` и добавляем `ProducerInterface`, исправляя конструктор:
        ```php
        /** @var ProducerInterface */
        private $producer;
    
        public function __construct(EntityManagerInterface $entityManager, ValidatorInterface $validator, SubscriptionService $subscriptionService, ProducerInterface $producer)
        {
            $this->entityManager = $entityManager;
            $this-validator = $validator;
            $this->subscriptionService = $subscriptionService;
            $this->producer = $producer;
        }
        ```    
    1. Исправляем метод `execute` (вместо материализации отправляем сообщения через producer)
        ```
        public function execute(AMQPMessage $msg): int
        {
            try {
                $message = Message::createFromQueue($msg->getBody());
                $errors = $this->validator->validate($message);
                if ($errors->count() > 0) {
                    return $this->reject((string)$errors);
                }
            } catch (JsonException $e) {
                return $this->reject($e->getMessage());
            }
        
            $tweetRepository = $this->entityManager->getRepository(Tweet::class);
            $tweet = $tweetRepository->find($message->getTweetId());
            if (!($tweet instanceof Tweet)) {
                return $this->reject(sprintf('Tweet ID %s was not found', $message->getTweetId()));
            }
        
            $followerIds = $this->subscriptionService->getFollowerIds($tweet->getAuthor()->getId());
        
            foreach ($followerIds as $followerId) {
                $this->producer->publish((new UpdateFeedMessage($tweet->getId(), $followerId))->toAMQPMessage(), $followerId);
            }
        
            $this->entityManager->clear();
            $this->entityManager->getConnection()->close();
        
            return self::MSG_ACK;
        }
        ```
1. В файл `config/services.yaml` добавляем инъекцию нового продюсера в консьюмер `TweetPublishedConsumer\Consumer` в
секцию `services`
    ```yaml
    App\Consumer\TweetPublishedConsumer\Consumer:
        autowire: true
        arguments:
            $producer: "@old_sound_rabbit_mq.update_feed.received_producer"
    ```
1. Запускаем все консьюмеры в фоновом режиме командами:
    ```shell script
    php bin/console rabbitmq:consumer tweet.published -m 100 &
    php bin/console rabbitmq:consumer update_feed.received-0 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-1 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-2 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-3 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-4 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-5 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-6 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-7 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-8 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-9 -m 1000 &
    ```
1. Посылаем запрос `Post tweet` из коллекции Postman и проверяем, что сообщение распределилось на новые консьюмеры
1. Добавляем файл `src/Client/StatsdAPIClient`
    ```
    <?php
    
    namespace App\Client;
    
    use Domnikl\Statsd\Client;
    use Domnikl\Statsd\Connection\UdpSocket;
    
    class StatsdAPIClient
    {
        private const DEFAULT_SAMPLE_RATE = 1.0;
        
        /** @var Client */
        private $client;
    
        public function __construct(string $host, int $port, string $namespace)
        {
            $connection = new UdpSocket($host, $port);
            $this->client = new Client($connection, $namespace);
        }
    
        public function increment(string $key, ?float $sampleRate = null, ?array $tags = null)
        {
            $this->client->increment($key, $sampleRate ?? self::DEFAULT_SAMPLE_RATE, $tags ?? []);
        }
    }
    ```
1. В файле `src/Consumer/UpdateFeedReceivedConsumer/Consumer.php`
    1. Добавляем идентификатор консьюмера и `StatsdAPIClient` в конструктор
        ```php
        /** @var StatsdAPIClient */
        private $statsdAPIClient;
        /** @var string */
        private $key;
        
        public function __construct(EntityManagerInterface $entityManager, ValidatorInterface $validator, FeedService $feedService, StatsdAPIClient $statsdAPIClient, string $key)
        {
            $this->entityManager = $entityManager;
            $this->validator = $validator;
            $this->feedService = $feedService;
            $this->statsdAPIClient = $statsdAPIClient;
            $this->key = $key;
        }
        ```
    1. Добавляем в метод `execute` увеличение счётчика обработанных сообщений конкретным консьюмером
        ```
        public function execute(AMQPMessage $msg): int
        {
            try {
                $message = Message::createFromQueue($msg->getBody());
                $errors = $this->validator->validate($message);
                if ($errors->count() > 0) {
                    return $this->reject((string)$errors);
                }
            } catch (JsonException $e) {
                return $this->reject($e->getMessage());
            }
        
            $tweetRepository = $this->entityManager->getRepository(Tweet::class);
            $tweet = $tweetRepository->find($message->getTweetId());
            if (!($tweet instanceof Tweet)) {
                return $this->reject(sprintf('Tweet ID %s was not found', $message->getTweetId()));
            }
        
            $this->feedService->putTweet($tweet, $message->getFollowerId());
        
            $this->entityManager->clear();
            $this->entityManager->getConnection()->close();
        
            $this->statsdAPIClient->increment($this->key);
            return self::MSG_ACK;
        }
        ```
1. Добавляем в `config/services.yaml` описание сервиса statsd API-клиента и инъекцию идентификаторов в консьюмеры
    ```
    App\Client\StatsdAPIClient:
        arguments:
            - graphite
            - 8125
            - my_app
    
    App\Consumer\UpdateFeedReceivedConsumer\Consumer0:
        class: App\Consumer\UpdateFeedReceivedConsumer\Consumer
        arguments:
            $key: 'update_feed_received_0'

    App\Consumer\UpdateFeedReceivedConsumer\Consumer1:
        class: App\Consumer\UpdateFeedReceivedConsumer\Consumer
        arguments:
            $key: 'update_feed_received_1'

    App\Consumer\UpdateFeedReceivedConsumer\Consumer2:
        class: App\Consumer\UpdateFeedReceivedConsumer\Consumer
        arguments:
            $key: 'update_feed_received_2'

    App\Consumer\UpdateFeedReceivedConsumer\Consumer3:
        class: App\Consumer\UpdateFeedReceivedConsumer\Consumer
        arguments:
            $key: 'update_feed_received_3'

    App\Consumer\UpdateFeedReceivedConsumer\Consumer4:
        class: App\Consumer\UpdateFeedReceivedConsumer\Consumer
        arguments:
            $key: 'update_feed_received_4'

    App\Consumer\UpdateFeedReceivedConsumer\Consumer5:
        class: App\Consumer\UpdateFeedReceivedConsumer\Consumer
        arguments:
            $key: 'update_feed_received_5'

    App\Consumer\UpdateFeedReceivedConsumer\Consumer6:
        class: App\Consumer\UpdateFeedReceivedConsumer\Consumer
        arguments:
            $key: 'update_feed_received_6'

    App\Consumer\UpdateFeedReceivedConsumer\Consumer7:
        class: App\Consumer\UpdateFeedReceivedConsumer\Consumer
        arguments:
            $key: 'update_feed_received_7'

    App\Consumer\UpdateFeedReceivedConsumer\Consumer8:
        class: App\Consumer\UpdateFeedReceivedConsumer\Consumer
        arguments:
            $key: 'update_feed_received_8'

    App\Consumer\UpdateFeedReceivedConsumer\Consumer9:
        class: App\Consumer\UpdateFeedReceivedConsumer\Consumer
        arguments:
            $key: 'update_feed_received_9'
            
    ```
1. В файл `config/packages/old_sound_rabbit_mq.yaml` в секции `old_sound_rabbit_mq.consumers` исправляем коллбэки для
каждого консьюмера на `App\Consumer\UpdateFeedReceivedConsumer\ConsumerK`
    ```yaml
    update_feed.received-0:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-0', routing_key: '1'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer0
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    update_feed.received-1:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-1', routing_key: '1'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer1
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    update_feed.received-2:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-2', routing_key: '1'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer2
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    update_feed.received-3:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-3', routing_key: '1'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer3
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    update_feed.received-4:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-4', routing_key: '1'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer4
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    update_feed.received-5:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-5', routing_key: '1'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer5
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    update_feed.received-6:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-6', routing_key: '1'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer6
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    update_feed.received-7:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-7', routing_key: '1'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer7
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    update_feed.received-8:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-8', routing_key: '1'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer8
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    update_feed.received-9:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-9', routing_key: '1'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer9
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
   ```
1. Перезапускаем все консьюмеры в фоновом режиме командами (при необходимости убиваем старые незавершившиеся):
    ```shell script
    php bin/console rabbitmq:consumer tweet.published -m 100 &
    php bin/console rabbitmq:consumer update_feed.received-0 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-1 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-2 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-3 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-4 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-5 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-6 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-7 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-8 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-9 -m 1000 &
    ```
1. Заходим в Grafana по адресу `localhost:3000` с логином / паролем `admin` / `admin`
1. Добавляем Data source с типом Graphite и url `http://graphite:80`
1. Импортируем Dashboard (Import) из файла RabbitMQ Statistics-1627296199454.json
1. Посылаем запрос `Post tweet` из коллекции Postman и видим на панели, что распределение между консьюмерами не
особенно равномерное
1. В файл `config/packages/old_sound_rabbit_mq.yaml` в секции `old_sound_rabbit_mq.consumers` исправляем параметры для
каждого консьюмера `updated_feed.received-K` (меняем значение `routing-key` на 20):
    ```yaml
    update_feed.received-0:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-0', routing_key: '20'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer0
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    update_feed.received-1:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-1', routing_key: '20'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer1
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    update_feed.received-2:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-2', routing_key: '20'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer2
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    update_feed.received-3:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-3', routing_key: '20'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer3
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    update_feed.received-4:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-4', routing_key: '20'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer4
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    update_feed.received-5:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-5', routing_key: '20'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer5
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    update_feed.received-6:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-6', routing_key: '20'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer6
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    update_feed.received-7:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-7', routing_key: '20'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer7
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    update_feed.received-8:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-8', routing_key: '20'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer8
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
    update_feed.received-9:
        connection: default
        exchange_options: {name: 'old_sound_rabbit_mq.update_feed.received', type: x-consistent-hash}
        queue_options: {name: 'old_sound_rabbit_mq.consumer.update_feed.received-9', routing_key: '20'}
        callback: App\Consumer\UpdateFeedReceivedConsumer\Consumer9
        idle_timeout: 300
        idle_timeout_exit_code: 0
        graceful_max_execution:
            timeout: 1800
            exit_code: 0
        qos_options: {prefetch_size: 0, prefetch_count: 1, global: false}
   ```
1. Перезапускаем все консьюмеры в фоновом режиме командами (при необходимости убиваем старые незавершившиеся):
    ```shell script
    php bin/console rabbitmq:consumer tweet.published -m 100 &
    php bin/console rabbitmq:consumer update_feed.received-0 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-1 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-2 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-3 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-4 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-5 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-6 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-7 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-8 -m 1000 &
    php bin/console rabbitmq:consumer update_feed.received-9 -m 1000 &
    ```
1. Ещё раз посылаем запрос `Post tweet` из коллекции Postman и видим на панели, что распределение между консьюмерами
стало гораздо равномернее
