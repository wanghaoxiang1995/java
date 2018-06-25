#Document APIs
* Single document APIs 单文档API
  * Index API
  * GET API
  * Delete API
  * Update API
* Multi-document APIs 多文档API
  * Multi Get API
  * Bulk API
  * Delete By Query API
  * Update By Query API
  * Reindex API
* 所有CRUD APIs为single-index APIs。`index`参数接受单个索引名，或者指向单个索引的`alias`
## Read and Writing documents
* 介绍
  每个Elasticsearch的索引将会分为分片shards，每个分片可以有多个复制。复制分片被称为复制组，在文档添加或移除时必须保持同步。如果失败了，从一个复制分片读出的结果回合另一个读出的不同。保持复制分片同步和读取服务的过程被称为数据复制模型data replication model。
  Elasticsearch数据复制模型基于主备模型primary-backup model。该模型基于复制组中作为主要分片的单独复制。另外的复制称为复制分片。主分片作为所有索引操作的主要进入点服务。需要验证他们，并保证他们的正确性。一旦一个索引操作被主要分片接受，主分片负债将操作复制到其他复制分片。
  这节的目的是提供一个Elasticsearch分片模型的大体概览，并讨论各种读写操作间的影响
* Basic write model 基础写模型
  Elasticsearch中每个索引操作首先使用`routing`解析到一个复制组，典型的，基于文档ID。一旦确定复制组，操作会在内部重定向到组中当前的主分片。主分片负责验证操作并将操作转发给其他复制。由于副本可以离线，主分片不会请求全部的复制分片复制，相反的，Elasticsearch会维护一个应该接受这个操作的复制分片的列表。这个列表称为in-sync copies并且被主节点维护。这个名称意味着他们是良好的能够保证所有的索引和删除操作处理通知到用户的通知到的复制分片集合。主要分片负责维护不变量并且复制所有操作到集合中的每个复制。
  主要分片遵守以下基本流程：
   1. 验证输入的操作并且拒绝非法的结构（例如：当需要一个数值时，具有一个对象字段）
   1. 执行本地操作，例如索引或删除相关文档，这同样验证字段内容并在必要时拒绝（例如：一个对于Lucene索引中过长的关键字）
   1. 转发操作给每个 current in-sync copies set 中的复制分片。如果有多个复制，操作是并行的
   1. 一旦所有复制成功执行操作并返回响应到主分片，主分片通知请求客户端成功完成
  * 失败处理
  有许多在索引期间导致失败的可能——磁盘可能损毁，节点可能与集群中其他节点断开连接，或者一些错误的配置会导致尽管在主分片上成功，在复制分片上会失败。这些是罕见情况，但是主分片需要对他们进行响应。
  有一种情况时主分片自身失败，主分片节点主机会向主节点发送一条相关消息。这个索引操作将会等待主节点（默认最多一分钟）来选拔一个其他的复制分片作为新的主分片。然后这个操作会转发给新的主节点进行处理。注意主节点同样会监控nodes并且可能决定堆一个主分片进行降级。典型的在一个从集群中隔离出去的节点持有主分片时会发生。
  一旦操作在主分片上成功执行，主分片必须处理当在复制分片上执行时潜在的失败。这可能由分片上实际的失败或由于网络问题阻止操作到达复制分片（或阻止复制分片的响应）。所有的这些公用相同的结果：在in-sync replica set中的部分遗失了一个将被通知的操作。为了避免违反一致，主分片发送一个信息给主节点请求从in-sync replica set中移除可能有问题的分片。一旦主节点通知主分片这个分片已经移除了，主分片响应这个操作。注意master同样会通知其他节点开始构建一个新的复制分片来修复系统到健康状态。
  当转发一个操作到复制，主分片会使用复制分片确认其仍是一个有效的主分片。如果主分片由于网络分割（或者一个长GC）从集群中隔离，在他认识到他已经被降级前，他可能继续处理进入的索引操作。来自一个过时的主分片的操作会被复制分片拒绝。当主分片从复制分片收到由于不再是主分片的拒绝响应，主分片将会接触主节点并且会获知替代的主分片。然后这个操作会路由到主分片。
  * 当没有复制分片时会发生什么？
     当由于索引配置或仅仅单纯是因为所有的复制分片都失败了，导致这种情况，有一个有效的方案。在这种情况下主分片不适用任何外部校验处理操作，看起来可能会有问题。在另一方面，主分片无法自行失效其他分片而需要请求主节点进行这个行为。这意味着主节点知道主分片时唯一正确的复制。因此我们保证主节点不会提升任何其他（过时的）分片复制作为新的主分片并且任意进入主分片的索引操作不会遗失。自然，由于我们仅在单个数据复制点上运行，物理硬件问题会导致数据丢失。在`Wait For Active Shards`查看一些控制对策
* Basic read model 基本读取模式
  在Elasticsearch中读取可以通过ID进行非常轻量级的查找或使用复杂聚合花费大量CPU处理能力的重量级搜索请求。一个主备模型的优势是他保持所有分片的独立（with the exception of in-flight operations）。同样的，单个in-sync copy足以进行read请求服务。
  当一个节点接收到read请求，这个节点负责转发请求到相关分片，整理响应，并响应客户端。我们称这个节点为这个请求的协调节点。基本流程如下：
   1. 解析读请求到相关分片。注意由于多数搜索请求将发送到一个或多个索引，他们一般需要从多个分片读取，每一个表示不同的数据子集
   1. 从每个相关分片复制组选择分片的一个存活复制。可以为主分片，也可以为复制分片。默认的，Elasticsearch将会简单的轮流使用分片的复制
   1. 发送分片级别的read请求到选中的分片
   1. 结合响应结果并进行响应。注意在使用ID查找时只有一个相关分片，这个步骤将被跳过
  * 失败处理
 当一个分片的读请求响应失败，这个协调节点将会从相同的复制组选择一个其他的复制并发送这个分片级别的搜索请求来代替这个复制。在没有可用的分片复制时会导致重复失败。在一些情况下，如`_search`，Elasticsearch将偏向快速响应，即使只有部分结果，而不是等待问题被处理（部分结果在响应`_shards`头中指明）
* A few simple implications
 所有这些基本流程决定了Elasticsearch作为同时读写系统的行为。另外，由于读写请求可以并发执行，两个基础行为会相互影响。这有几个内在含义：
  1. Efficient reads
      一般操作下，每个读操作会在所有相关复制组上执行一次。仅在失败条件下同一分片的多个复制才会执行相同的搜索
  1. Read unacknowledged
      由于主分片先索引本地，然后复制请求，并发的读取可能在变更可见之前通知响应
  1. Two copies by default
      这个模型在仅维护两个数据复制时是容错的。这与基于指定数量的系统不同，它的最小容错数量为3
* Failures
  在失败时，有下面的可能
   1. A single shard can slow down indexing
      由于在每个操作期间主分片等待所有in-sync copies set中的索引，一个迟缓的分片会减慢整个复制组。这是我们为了前面提到的高效读取付出的代价。当然，缓慢的当个分片同样会减慢被路由到他的搜索
   1. Dirty reads
      一个隔离的主分片能够暴露写入并且不会进行通知。这是由于一个隔离的主分片仅在他向它的复制分片发送请求或是当无法访问主节点时才会认识到他被隔离了的事实导致的。这时操作已经被索引到这个主分片并且能被一个并发read读取。Elasticsearch通过每秒（默认）ping主节点并且在没有已知的主节点时拒绝索引操作来减少这种风险
* The Tip of the Iceberg
  这个文档提供Elasticsearch处理数据的一个高层概览。当然，还有很多事情要做。如primary terms,cluster state publishing 和 master election 都在使系统保持正确行为中发挥作用。这个文档同样没有掩盖已知的和重要的bugs（包括closed和open）。很难跟上GitHub。ELasticsearch在他们的网站上维护一个页面 https://www.elastic.co/guide/en/elasticsearch/resiliency/current/index.html 。强烈建议阅读它。
## Index API
* Index API 新增或更新一个输入的JSON文档到指定的索引，使他可被搜索。下面的例子插入JSON文档到"twitter"索引，type为"_doc"（Elasticsearch准备移除mapping types，默认使用"_doc"作为过渡type），id为1：
  ```
  PUT twitter/_doc/1
  {
      "user" : "kimchy",
      "post_date" : "2009-11-15T14:12:12",
      "message" : "trying out Elasticsearch"
  }
  ```
  * 上面的索引操作的结果为：
  ```
  {
    "_shards" : {
        "total" : 2,
        "failed" : 0,
        "successful" : 2
    },
    "_index" : "twitter",
    "_type" : "_doc",
    "_id" : "1",
    "_version" : 1,
    "_seq_no" : 0,
    "_primary_term" : 1,
    "result" : "created"
  }
  ```
  * `_shards`头提供这次索引操作分片处理信息。
    * `total`-指明有多少个应该执行索引操作的分片复制（主要和复制分片）
    * `successful`-指明有多少个分片复制索引操作成功
    * `failed`-如果索引操作在一个复制分片失败的情形，则在包含相关错误的复制分片数组
  * 在`successful`至少为1时，索引操作成功
    ```
    当索引操作返回成功时可能不是所有的复制分片都启动了（默认的，仅要求主分片成功，但是这个行为可以改变）。这种情况下，total将等于基于number_of_replicas设定的全部分片，并且successful要等于启动的分片数量（主分片加上复制分片）。如果这没有失败，failed将为0
    ```
* Automatic Index Creation
  * 当索引不存在时进行index操作会自动创建索引（使用create index API手动维护），并且在指定类型没有被创建时会自动建立一个动态type mapping（使用put mapping API手动维护）
  * mapping本身非常灵活并且是格式自由的。新的字段和对象会自动添加到mapping定义中进行类型指定。
  * 自动索引建立可以通过在所有节点配置文件中设置`action.auto_create_index`为`false`进行禁用。自动mapping创建可以通过对索引设置索引设定`index.mapper.dynamic`为`false`禁用
  * 自动索引建立可以包含一个基于黑/白名单列表的模式匹配，如设置`action.auto_create_index`为`+aaa*,-bbb*,+ccc*,-*`（+代表允许，-代表禁用）
* Versioning
  * 每个被索引的文档会被给定一个版本号。相关的`version`数值会作为index API请求响应返回的一部分返回。index API操作在指定`version`参数时，允许乐观并发控制。一个不错的versioning用例是执行一个读取后更新的事务。指定一个来自原始文档读取的`version`确保在期间文档没有发生改变（当为了进行更新读取数据时，推荐设置`preference`为`_primary`）。例如：
  ```
  PUT twitter/_doc/1?version=2
  {
      "message" : "elasticsearch now has versioning support, double cool!"
  }
  ```
  * 注意：versioning是完全实时的，并且不会被近实时的搜索操作影响。如果没有提供version，那么这个操作将会不进行任何版本检查执行
  * 默认的，将使用从1开始，并且在每次包括更新|删除时增长1作为内部版本。版本数值能够使用外部值作为补充（例如在一个数据库中维护）。为了是使用这个功能，`version_type`应该被设为`external`。被提供的值必须为大于等于0，小于约9.2e+18的数值。当使用外部版本类型时，系统会检查index请求中的版本数值是否大于当前存储文档的版本，而不是检查版本数值是否匹配。如果通过，文档将被索引，新的版本数值将被应用。如果提供的值小于等于存储的文档的版本数值，将会发生版本冲突，这个索引操作将会失败
  ```
  外部版本支持0为一个有效的版本值。这允许使用从0而不是1开始的外部版本系统控制。它的副作用是只要版本号为0就不能用来使用Update-By-Query API或Delete By Query API
  ```
  * 一个不错的副作用是不需要在一个异步索引操作中执行源数据库改变结果维护严格的顺序，只要版本值来自使用的源数据库。即使简单的使用来自数据库的数据进行简单的外部版本使用，来更新Elasticsearch，为了没有任何不一致的原因，只有最后的版本将被用于索引操作
* Version types
  * 除了上面说的`internal`和`external`版本类型，Elasticsearch同样支持其他类型来指定用例。这里是一个不同版本类型和其意义差异的一个概览：
    * `internal` 仅在给出版本与存储文档版本一致时索引文档
    * `external`或`external_gt` 尽在给出的版本严格高于存储文档版本或文档不存在时索引文档。这个给出的版本将被用来作为将被存储的新文档的版本。支持的version必须为非负的long number。
    * `external_gte` 仅在给出的版本大于等于存储文档版本时索引文档。如果没有已经存在的文档时操作也将成功。给出的版本将被作为新的存储文档的版本。支持的version为非负long number
  * 注意：`external_gte`版本类型意味着特定的使用情形，应该小心使用。如果使用错误，可能导致数据丢失。另一个操作`force`，由于可能导致主分片和复制分片的差异，现在已经弃用
* Operation Type
  * 索引操作同样接受一个`op_type`，能够接受`create`动作，允许"put-if-absent"。当`create`已被使用，索引动作将失败
  * 一个使用`op_type`参数的例子：
  ```
  PUT twitter/_doc/1?op_type=create
  {
      "user" : "kimchy",
      "post_date" : "2009-11-15T14:12:12",
      "message" : "trying out Elasticsearch"
  }
  ```
  * 另一个使用uri指定create的选项：
  ```
  PUT twitter/_doc/1/_create
  {
      "user" : "kimchy",
      "post_date" : "2009-11-15T14:12:12",
      "message" : "trying out Elasticsearch"
  }
  ```
* Automatic ID Generation
  * 这个索引操作可以不指定id执行。在这种情况，id将会自动生成。另外，`op_type`将自动被设为`create`。这里有一个例子（注意使用POST代替PUT）：
  ```
  POST twitter/_doc/
  {
      "user" : "kimchy",
      "post_date" : "2009-11-15T14:12:12",
      "message" : "trying out Elasticsearch"
  }
  ```
  * 以上操作的结果为：
  ```
  {
      "_shards" : {
          "total" : 2,
          "failed" : 0,
          "successful" : 2
      },
      "_index" : "twitter",
      "_type" : "_doc",
      "_id" : "W0tpsmIBdwcYyG50zbta",
      "_version" : 1,
      "_seq_no" : 0,
      "_primary_term" : 1,
      "result": "created"
  }
  ```
* Routing
  * 默认的，分片位置--或`routing`--通过使用一个文档id的hash控制。如果要更明确的控制，可以在每个操作基础上直接使用`routing`参数指定进入hash函数的值。例如：
  ```
  POST twitter/_doc?routing=kimchy
  {
      "user" : "kimchy",
      "post_date" : "2009-11-15T14:12:12",
      "message" : "trying out Elasticsearch"
  }
  ```
  * 在上面的例子中，这个"_doc"文档被路由到的分片基于提供的`routing`参数“kimchy"
  * 当设置一个明确的mapping，`_routing`字段能够被选择使索引操作直接从文档本身获取路由值。这需要（极少的）附加的文档解析的消耗
* Distributed
  * index基于它的route(查看Routing部分)直接到主要分片，并且在实际包含这个分片的节点执行。在主要分片完成操作以后，如果有需要，更新会分发到合适的复制分片上
* Wait For Active Shards
  * 为了提高写入到系统的弹性，索引操作可以配置为在处理操作之前等待确定数量的活动分片复制。如果没有必须数量的可用活动分片复制，写入操作必须等待并重试，直到需求数量的分片复制启动或者发生超时。默认的，写操作在处理之前仅等待主分片活动（`wait_for_active_shards=1`）。默认值可以通过设置`index.write.wait_for_active_shards`索引设定动态设置。为了选择每个操作的行为，可以使用`wait_for_active_shards`请求参数
  * 有效值为`all`或任何小于等于索引中配置每分片复制数量（为`number_of_replicas+1`）总数量的正整数。指定一个负数值或者大于分片复制数量的数会抛出一个错误
  * 例如，假设我们有一个三节点集群，A，B和C，我们建立一个索引`index`，设置the number of replicas set to 3(结果将产生4个分片复制，比节点数量还要多一个)。如果我们尝试一次索引操作，默认的这次操作仅确保每个分片的主要分片在处理前可用。这意味着即使B和C不在线，A具有主要分片复制，索引操作将仍会仅对这一份数据复制进行处理。如果`wait_for_active_shards`设置为`all`（或者设为`4`，具有相同的效果)，当我们在所有节点上更没有全部的4个分片复制时，这个索引操作将会失败。除非一个新的节点加入集群来持有第四个分片复制，这个操作将会超时
  * 注意这个设定能很好的减少写入操作没有写入到必须数量的分片复制的几率，但是不能完全排除这种可能性，因为检查发生在操作开始之前这点是很重要的。一旦写操作开始，仍有可能在主分片成功时有任意数量的复制分片失败。写操作的响应的`_shards`片段显示所有复制分片分片复制的成功/失败数量
  ```
  {
      "_shards" : {
          "total" : 2,
          "failed" : 0,
          "successful" : 2
      }
  }
  ```
* Refresh
  * 控制使这个请求的变动对搜索可见。
* Noop Updates
  * 当更新一个文档使用索引api,这个文档总是建立一个新的版本，即使文档没有改变。使用设置`detect_noop`为true的`_update` api将不接受无改变的文档。这个操作在index api中不可用，因为索引api不会去获取老的源，无法将他和新的源比较
  * 在何时不接受noop updates并没有硬性规定。有大量的因素，如你的数据源发送实际noops更新的频率与接受更新的分片上Elasticsearch运行中每秒查询的次数
* Timeout
  * 分配主要分片执行索引操作在索引操作执行过程中可能不可用。一些原因可能像是主要分片正在从一个网关恢复或者正在重新分配。默认的，索引操作在返回失败和错误响应前最多在一分钟内等待主要分片可用。`timeout`参数可以用来明确指定等待时长。下面是设置为5分钟的例子：
  ```
  PUT twitter/_doc/1?timeout=5m
  {
      "user" : "kimchy",
      "post_date" : "2009-11-15T14:12:12",
      "message" : "trying out Elasticsearch"
  }
  ```