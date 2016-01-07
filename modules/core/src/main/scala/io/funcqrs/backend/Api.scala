package io.funcqrs.backend

import io.funcqrs._
import io.funcqrs.behavior.Behavior

import scala.concurrent.Future
import scala.language.higherKinds


trait Api[F[_]] {

  def config(projectionConfig: ProjectionConfig)(implicit backend: Backend[F]): Unit = {
    backend.configureProjection(projectionConfig)
  }

  def config[A <: AggregateLike](aggregateConfig: AggregateConfigWithAssignedId[A])(implicit backend: Backend[F]): AggregateServiceWithAssignedId[A, F] = {
    backend.configureAggregate(aggregateConfig)
  }

  def config[A <: AggregateLike](aggregateConfig: AggregateConfigWithManagedId[A])(implicit backend: Backend[F]): AggregateServiceWithManagedId[A, F] = {
    backend.configureAggregate(aggregateConfig)
  }

  def aggregate[A <: AggregateLike](behavior: A#Id => Behavior[A]): AggregateConfigWithAssignedId[A] = {
    AggregateConfigWithAssignedId[A](None, behavior, AssignedIdStrategy[A])
  }

  def projection(publisherProvider: EventsPublisherProvider,
                 projection: Projection,
                 name: String): ProjectionConfig = {
    ProjectionConfig(publisherProvider, projection, name)
  }
}


// ================================================================================
// support classes and trait for AggregateService creation!
trait Config

trait AggregateConfig[A <: AggregateLike] extends Config {

  def name: Option[String]

  def behavior: (A#Id) => Behavior[A]

  def idStrategy: AggregateIdStrategy[A]

  def withName(name: String): AggregateConfig[A]

  /**
    * Configure Aggregate to use an [[AssignedIdStrategy]].
    *
    * Aggregate Ids are defined externally.
    */
  def withAssignedId: AggregateConfigWithAssignedId[A] = {
    AggregateConfigWithAssignedId(name, behavior, AssignedIdStrategy[A])
  }

  /**
    * Configure Aggregate to use an [[GeneratedIdStrategy]].
    * On each create command, a new unique Id will be generated.
    *
    * @param gen - a by-name parameter that should, whenever evaluated, return a unique Aggregate Id
    */
  def withGeneratedId(gen: => A#Id): AggregateConfigWithManagedId[A] = {
    val strategy = new GeneratedIdStrategy[A] {
      def generateId(): Id = gen
    }
    AggregateConfigWithManagedId(name, behavior, strategy)
  }

  /**
    * Configure Aggregate to use a fixed Id.
    *
    * A [[SingletonIdStrategy]] will be constructed using the passed Id
    * @param uniqueId - the fixed Id to be used for this Aggregate
    */
  def withSingletonId(uniqueId: A#Id): AggregateConfigWithManagedId[A] = {
    val strategy = new SingletonIdStrategy[A] {
      val id: Id = uniqueId
    }
    AggregateConfigWithManagedId(name, behavior, strategy)
  }

}

case class AggregateConfigWithAssignedId[A <: AggregateLike](name: Option[String],
                                                             behavior: (A#Id) => Behavior[A],
                                                             idStrategy: AggregateIdStrategy[A]) extends AggregateConfig[A] {

  def withName(name: String): AggregateConfigWithAssignedId[A] =
    this.copy(name = Option(name))

}

case class AggregateConfigWithManagedId[A <: AggregateLike](name: Option[String],
                                                            behavior: (A#Id) => Behavior[A],
                                                            idStrategy: AggregateIdStrategy[A]) extends AggregateConfig[A] {

  def withName(name: String): AggregateConfigWithManagedId[A] =
    this.copy(name = Option(name))

}

trait IdStrategy

trait AssignedId extends IdStrategy

trait ManagedId extends IdStrategy

// ================================================================================

// ================================================================================
// support classes and trait for Projection creation!
trait OffsetPersistenceStrategy

case object NoOffsetPersistenceStrategy extends OffsetPersistenceStrategy

case class BackendOffsetPersistenceStrategy(persistenceId: String) extends OffsetPersistenceStrategy

trait CustomOffsetPersistenceStrategy extends OffsetPersistenceStrategy {

  def saveCurrentOffset(offset: Long): Future[Unit]

  /** Returns the current offset as persisted in DB */
  def readOffset: Future[Option[Long]]

}

case class ProjectionConfig(publisherProvider: EventsPublisherProvider,
                            projection: Projection,
                            name: String,
                            offsetPersistenceStrategy: OffsetPersistenceStrategy = NoOffsetPersistenceStrategy) {

  def withoutOffsetPersistence: ProjectionConfig = {
    this.copy(offsetPersistenceStrategy = NoOffsetPersistenceStrategy)
  }

  def withBackendOffsetPersistence: ProjectionConfig = {
    this.copy(offsetPersistenceStrategy = BackendOffsetPersistenceStrategy(name))
  }

  def withCustomOffsetPersistence(strategy: CustomOffsetPersistenceStrategy): ProjectionConfig = {
    this.copy(offsetPersistenceStrategy = strategy)
  }

}
