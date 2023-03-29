import zio._
import zio.Console.printLine

// DOMAIN
case class User(name: String, message: String)

// DEFINITIONS
class SubscriptionService(emailer: EmailService, db: DbService) {
  def subscribe(user: User): Task[Unit] =
    for {
      _ <- emailer.email(user)
      _ <- db.insert(user)
    } yield ()
}
object SubscriptionService {
  def create(emailService: EmailService, dbService: DbService) = new SubscriptionService(emailService, dbService)
  // ones with dependencies use "fromFunction", ones without "succeed"
  val live: ZLayer[EmailService with DbService, Nothing, SubscriptionService] =
    ZLayer.fromFunction(create _)
}

class EmailService {
  def email(user: User): Task[Unit] = printLine(s"email ${user.message} sent to ${user.name}")
}
object EmailService {
  def create() = new EmailService()
  val live: ZLayer[Any, Nothing, EmailService] =
    ZLayer.succeed(create())
}

class DbService(conp: ConnectionPool) {
  def insert(user: User): Task[Unit] =
    for {
      con <- conp.get
      _ <- con.runQuery("insert query")
    } yield ()
}
object DbService {
  def create(connectionPool: ConnectionPool) = new DbService(connectionPool)
  val live: ZLayer[ConnectionPool, Nothing, DbService] =
    ZLayer.fromFunction(create _)
}

class ConnectionPool(connections: Int) {
  def get: Task[Connection] =
    printLine("connection pool service, getting: " + connections) *> ZIO.succeed(new Connection())
}
object ConnectionPool {
  def create(connections: Int) = new ConnectionPool(connections)
  def live(con: Int): ZLayer[Any, Nothing, ConnectionPool] =
    ZLayer.succeed(create(con))
}

class Connection() {
  def runQuery(query: String): Task[Unit] =
    printLine(s"executing: $query")
}
object Connection {
  def create() = new Connection()
  val live: ZLayer[Any, Nothing, Connection] =
    ZLayer.succeed(create())
}




object Main extends ZIOAppDefault {

  // OLD WAY OF COMPOSING SERVICES
  val subscriptionService = ZIO.succeed(
    SubscriptionService.create(
      EmailService.create(),
      DbService.create(
        ConnectionPool.create(3)
      )
    )
  )

  // DATA
  val user = User("man", "mans message")

  // OLD WAY
  def oldWay(user: User): ZIO[Any, Throwable, Unit] =
    for {
      sub <- subscriptionService // istantiated at the call :(
      _ <- sub.subscribe(user)
    } yield ()

  // NEW WAY
  def newWay(user: User): ZIO[SubscriptionService, Throwable, Unit] =
    for {
      sub <- ZIO.service[SubscriptionService]
      _ <- sub.subscribe(user)
    } yield ()



  override def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] = {
    // oldWay(user)

    // new way what would work
//    newWay(user)
//      .provide(
//        ZLayer.succeed(
//          SubscriptionService.create(
//            EmailService.create(),
//            DbService.create(
//              ConnectionPool.create(3)
//            )
//          )
//        )
//      )

    // ZIO automatically detects dependencies via macros
    newWay(user)
      .provide(
        SubscriptionService.live,
        EmailService.live,
        DbService.live,
        ConnectionPool.live(32),
        //
        ZLayer.Debug.mermaid // check in Build Output
      )

     // bonus
     System.env("hallo_env_val")
  }
}





