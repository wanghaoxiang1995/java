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
## Get API
get API允许从索引中基于它的id获取JSON类型的文档。下面的例子是从称为twitter的索引中，从_doc类型下获取id值为0的JSON文档：
```
GET twitter/_doc/0
```
上面的get操作的结果为：
```
{
    "_index" : "twitter",
    "_type" : "_doc",
    "_id" : "0",
    "_version" : 1,
    "found": true,
    "_source" : {
        "user" : "kimchy",
        "date" : "2009-11-15T14:12:12",
        "likes": 0,
        "message" : "trying out Elasticsearch"
    }
}
```
上面的结果包含我们希望获取的文档的`_index`，`_type`，`_id`和`_version`，如果能够找到的话包含文档实际的`_source`。
这个API同样允许使用`HEAD`检查文档是否存在，如：
```
HEAD twitter/_doc/0
```
* Realtime
  * 默认的，get API是实时的，并且不被索引刷新率（when data will become visible for search）影响。如果一个文档被更新，但是还没有刷新，get API 会发出一个refresh call in-place使文档可见。这同样使上次刷新以来的其他文档改变可见。为了关闭实时GET，一种方式是可以设置`realtime`参数为false
* Source filtering
  * 默认的，get操作返回`_source`字段内容，除非你使用`stored_fields`参数或者`_source`字段被禁用。你可以通过使用`_source`参数关闭获取`_source`
  ```
  GET twitter/_doc/0?_source=false
  ```
  * 如果你仅需要一个或两个来自完整的`_source`的字段，你可以使用`_source_include`和`_source_exclude`参数来包含或过滤出你需要的部分。这在大文档部分获取时非常有用，能够节省网络开支。使用逗号分割列表或者通配表达式的参数都被接受。例如：
  ```
  GET twitter/_doc/0?_source_include=*.id&_source_exclude=entities
  ```
  * 如果你像指定包含的，你可以使用一个简短的表示：
  ```
  GET twitter/_doc/0?_source=*.id,retweeted
  ```
* Stored Fields
  * get 操作允许通过使用`stored_fields`字段指定返回的存储字段。付过请求字段没有被存储，将会被忽略。考虑像下面这个mapping：
  ```
  PUT twitter
  {
     "mappings": {
        "_doc": {
           "properties": {
              "counter": {
                 "type": "integer",
                 "store": false
              },
              "tags": {
                 "type": "keyword",
                 "store": true
              }
           }
        }
     }
  }
  ```
  * 现在添加一个文档：
  ```
  PUT twitter/_doc/1
  {
      "counter" : 1,
      "tags" : ["red"]
  }
  ```
  * 尝试获取他：
  ```
  GET twitter/_doc/1?stored_fields=tags,counter
  ```
  * 上面的get操作的结果为
  ```
  {
     "_index": "twitter",
     "_type": "_doc",
     "_id": "1",
     "_version": 1,
     "found": true,
     "fields": {
        "tags": [
           "red"
        ]
     }
  }
  ```
  * 从文档本身获取的字段值总是返回一个数组。由于没有存储`counter`字段，这个请求在获取`stored_fields`时会简单的忽略它
  * 获取如`_routing`字段的源数据字段也是可能的
  ```
  PUT twitter/_doc/2?routing=user1
  {
      "counter" : 1,
      "tags" : ["white"]
  }
  ```
  ```
  GET twitter/_doc/2?routing=user1&stored_fields=tags,counter
  ```
  * 上面的get操作结果为：
  ```
  {
     "_index": "twitter",
     "_type": "_doc",
     "_id": "2",
     "_version": 1,
     "_routing": "user1",
     "found": true,
     "fields": {
        "tags": [
           "white"
        ]
     }
  }
  ```
  * 同样仅有叶子字段能够通过`stored_field`选项返回。所以对象字段无法被返回，这样的请求将会失败
* Getting the `_source` directly
  * 使用`/{index}/{type}/{id}/_source`进入点不适用任何附加内容时，只获取文档`_source`字段。如：
  ```
  GET twitter/_doc/1/_source
  ```
  * 你同样可以使用相同的源过滤器参数来控制返回`_source`中的哪个部分：
  ```
  GET twitter/_doc/1/_source?_source_include=*.id&_source_exclude=entities'
  ```
  * 注意，_source进入点同样有一个HEAD变体可以有效的测试文档_source是否存在。一个存在的文档在mapping中禁用_source时，将不会有一个_source。
  ```
  HEAD twitter/_doc/1/_source
  ```
* Routing
  * 当索引使用路由控制能力时，为了获取到一个文档，routing值同样应该被提供。例如：
  ```
  GET twitter/_doc/2?routing=user1
  ```
  * 上面将会获取到id为2的tweet，但是路由基于user。注意，发出一个带有不正确路由的get，将会无法获取到文档
* Preference
  * 控制使用哪个分片复制执行get请求的`preference`。默认的，这个动作在分片复制之间是随机的
  * `preference`可以设为：
    * `_primary` 这个操作将仅发给主分片并执行
    * `_local` 这个操作在可能的情况下优先在本地分配分片执行
    * Custom （string）value 一个自定义值将被用来保证相同分片使用同样的自定义值。这可以帮助在refresh状态命中不同分片时跳过值。一个简单的值可以像是web session id 或者 user name
* Refresh
  * `refresh`参数可以被设为true来使相关分片在get操作前refresh并使其可被搜索。应该在仔细考虑和验证过它不会导致严重的系统负载（和减慢索引）后才将它设置为true
* Distributed
  * get操作散列到特定的分片id。然后get转发到这个分片id的一个复制并返回结果。这个复制可以是这个分片id组中的主要分片和它的复制分片。这意味着我们有越多的复制分片，我们有越好的GET扩展
* Versioning support
  * 仅当当前的版本等于指定的版本时，你可以使用`version`参数来获取文档。这个行为与所有除了总是会获取文档的`FORCE`外的version types相同。注意FORCE已经废弃了
  * 本质上，Elasticsearch标记老的文档为已删除并添加一个完整的新文档。虽然你无法访问老的文档版本了，他不会立即消失。Elasticsearch在你继续索引更多数据时在后台清理删除的文档
## Delete API
* delete API允许从指定索引中根据id删除一个已输入的JSON文档。下面是从一个索引twitter下的_doc类型删除id为1的JSON文档：
```
DELETE /twitter/_doc/1
```
* 上面的删除操作结果为：
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
    "_version" : 2,
    "_primary_term": 1,
    "_seq_no": 5,
    "result": "deleted"
}
```
* Versioning
  * 每个文档被索引都被标记版本。当删除一个文档时，可以指定`version`来确保我们尝试删除的相关文档确实在被删除并且期间没有更改。每个对一个文档执行的写操作，包括删除，会导致它的版本增长。删除的文档的版本号在删除后的小段时间内仍然可用，来允许当前操作控制。这个删除的文档版本保留可用的时间长度在`index.gc_deletes`索引设置定义并且默认值为60秒
* Routing
  * 当索引使用路由控制能力时，为了删除一个文档，路由值也应该被提供，例如：
  ```
  DELETE /twitter/_doc/1?routing=kimchy
  ```
  * 上面的将会删除id为1的tweet文档，但是将会基于user路由。注意，发起一个未正确路由的删除，将不会删除文档
  * 当`_routing` mapping设置为`required`并且没有指定路由值，删除api将抛出一个`RoutingMissingException`并且拒绝这个请求
* Automatic index creation
  * 如果一个外部版本变量被使用，如果在之前没有被创建，删除操作自动创建一个索引（查看create index API如何手动建立一个索引），同样在之前没有创建指定的类型时，自动建立一个动态的type mapping来指定type（查看put mapping API如何手动建立一个type mapping）
* Distributed
  * 删除操作散列到特定的分片id，然后转发到这个id组的主要分片，和复制（如果有需要）来分享到id组的复制分片
* Wait For Active Shards
  * 当进行一个删除请求，你可以设置`wait_for_active_shards`参数来要求一个开始处理删除请求前最小活动分片复制数量
* Refresh
  * 控制发生改变的请求何时对搜索可见
* Timeout
  * 在执行删除操作时，分配的主要分片执行删除操作时可能不可用，一些原因可能是主要分片当前正在从存储中恢复或是正在重新分配。默认的，删除操作在失败并响应一个错误前最多等待一分钟等待主要分片可用。`timeout`参数能够用来明确指定等待多长时间。下面是一个设定等待5分钟的例子：
  ```
  DELETE /twitter/_doc/1?timeout=5m
  ```
## Delete By Query API
* `_delete_by_query`最简单的用法就是对每个匹配查询的文档执行删除。API像这样：
```
POST twitter/_delete_by_query
{
  "query": { 
    "match": {
      "message": "some message"
    }
  }
}
```
* 这个查询必须通过与Search API相同的方式，传递值到`query`键。你同样可以用与search api相同的方式使用`q`参数
* 这将返回像这样一些东西：
```
{
  "took" : 147,
  "timed_out": false,
  "deleted": 119,
  "batches": 1,
  "version_conflicts": 0,
  "noops": 0,
  "retries": {
    "bulk": 0,
    "search": 0
  },
  "throttled_millis": 0,
  "requests_per_second": -1.0,
  "throttled_until_millis": 0,
  "total": 119,
  "failures" : [ ]
}
```
* `_delete_by_query`获取一个开始时的索引的快照，并且使用内部版本管理删除他找到的内容。这意味着如果在快照产生和删除请求处理期间有文档改变，你将得到一个版本冲突。当版本匹配时，文档被删除
* 由于内部版本控制不支持作为一个有效版本号，版本号为的文档不能使用`_delete_by_query`删除，并将响应失败
* 在`_delete_by_query`执行期间，为了找到所有匹配的文档进行删除，多个搜索请求被连续执行。每次找到一批文档，执行一个相应的bulk请求来删除所有这些文档。在搜索或bulk请求被拒绝的情况下，`_delete_by_query`依赖于一个默认策略来重试拒绝的请求（最多10次，使用指数退避）。达到最大尝试现在导致`_delete_by_query`中断并且所有失败在响应中的`failures`返回。已经执行的删除操作仍然有效。换句话说，这个过程无法回退，只会中断。当第一次失败导致一个中断，所有的失败会被返回，失败的bulk请求返回到`failures`元素中，因此有相当多的失败实体是有可能的。
* 如果你想要算入版本冲突而不是让他们引起中断，那么可以设置`conflicts=proceed`到url或者`"conflits":"proceed"`到请求体中
* 回到API格式，这将会从twitter索引中删除tweets：
```
POST twitter/_doc/_delete_by_query?conflicts=proceed
{
  "query": {
    "match_all": {}
  }
}
```
* 一次删除多个索引和多个类型也是可行的，就像下面的搜索API这样：
```
POST twitter,blog/_docs,post/_delete_by_query
{
  "query": {
    "match_all": {}
  }
}
```
* 如果你提供了`routing`，路由会复制到这个scroll query，限制这个处理在匹配路由值的分片上进行
```
POST twitter/_delete_by_query?routing=1
{
  "query": {
    "range" : {
        "age" : {
           "gte" : 10
        }
    }
  }
}
```
* 默认的，`_delete_by_query`使用scroll一批处理1000。你可以使用`scroll_size` URL参数改变批大小：
```
POST twitter/_delete_by_query?scroll_size=5000
{
  "query": {
    "term": {
      "user": "kimchy"
    }
  }
}
```
* URL Parameters
  * 标准的附加参数如`pretty`，the Delete By Query API 同样支持`refresh`，`wait_for_completion`，`timeout`和`scroll`
  * 发送`refresh`一旦请求完成，将会刷新所有这次delete by query有关分片。这与Delete API的`refresh`参数不同，Delete API的`refresh`参数仅导致接受delete请求的分片被刷新
  * 如果请求包含`wait_for_completion=false`那么Elasticsearch将执行一些准备检查，开始这个请求，然后返回一个`task`，可以使用Tasks APIs来取消或者获取task状态。
  * Elasticsearch同样将创建一条task的记录作为一个文档到`.tasks/task/${taskId}`。你可以根据情况保留或删除。当你完成他时，删除他来归还它使用的空间。
  * `wait_for_active_shards`控制在处理请求之前至少有多少活动分片复制。`timeout`控制每次请求等待不可用的分片可用多长时间。work指明在Bulk API中如何工作。由于`_delete_by_query`使用scroll搜索，你也可以指定`scroll`参数控制保持"search context"存活时间，如`?scroll=10m`，默认为5分钟
  * `requests_per_second`能够设为任何正数（1.4,6,1000...），通过使用等待时间填补每次批处理时间，控制`_delete_by_query`发起的批量删除操作速率。阈值可以通过设置`requests_per_second`为`-1`禁用
  * 阈值通过在批处理之间等待完成，所以`_delete_by_query`内部使用的scroll可以给定一个考虑到填充的超时时间。填充时间对在批大小被不同的`requests_per_second`除和写入时间是不同的。默认的批大小为1000，所以如果`requests_per_second`设为`500`：
  ```
  target_time = 1000 / 500 per second = 2 seconds
  wait_time = target_time - write_time = 2 seconds - .5 seconds = 1.5 seconds
  ```
  * 由于批处理作为单个`_bulk`请求发起，大的批大小会使Elasticsearch创建许多请求然后等待一段时间，再开始下一组。使用"bursty"而不是"smooth"。默认为-1
* Response body
  * JSON响应像下面这样：
  ```
  {
    "took" : 147,
    "timed_out": false,
    "total": 119,
    "deleted": 119,
    "batches": 1,
    "version_conflicts": 0,
    "noops": 0,
    "retries": {
      "bulk": 0,
      "search": 0
    },
    "throttled_millis": 0,
    "requests_per_second": -1.0,
    "throttled_until_millis": 0,
    "failures" : [ ]
  }
  ```
  * `took` 整个操作从开始到结束花费的毫秒数
  * `timed_out` 如果有任何请求在delete by query期间执行超时了，这个标记为true
  * `total` 成功处理的文档数
  * `deleted` 删除的文档数
  * `batches` delete by query获取scroll响应的数量
  * `version_conflicts` delete by query命中的版本冲突数量
  * `noops` delete by query时这个字段总等于0。他存在仅仅时因为delete by query,update by query 和 reindex APIs使用同样的结构返回结果
  * `retries` 重试delete by query尝试次数。`bulk`是bulk动作重试次数，`search`是search动作重试次数
  * `throttled_millis` 为了遵守`requests_per_second`请求睡眠的时间
  * `requests_per_second` 在delete by query期间每秒有效执行的请求数
  * `throttled_until_millis` 这个字段在delete by query响应中应该总是等于0。他仅意味着在使用Task API时，他指明一个throttled请求为了遵守`requests_per_second`下一次被执行的时间（从epoch起毫秒数）
  * `failures` 处理期间任何不会恢复的错误的数组。如果这个不是空的，那么这个请求会因为这些失败中止。Delete-by-query使用batches实现，所有的失败导致整个处理过程中止，但是所有当前失败的批被搜集到这个数组。你可以使用`conflicts`选项阻止从版本冲突中止重新索引
* Works with the Task API
  * 你可以使用Task API获取任何运行中的delete-by-query请求的状态：
  ```
  GET _tasks?detailed=true&actions=*/delete/byquery
  ```
  * 响应看起来像这样：
  ```
  {
    "nodes" : {
      "r1A2WoRbTwKZ516z6NEs5A" : {
        "name" : "r1A2WoR",
        "transport_address" : "127.0.0.1:9300",
        "host" : "127.0.0.1",
        "ip" : "127.0.0.1:9300",
        "attributes" : {
          "testattr" : "test",
          "portsfile" : "true"
        },
        "tasks" : {
          "r1A2WoRbTwKZ516z6NEs5A:36619" : {
            "node" : "r1A2WoRbTwKZ516z6NEs5A",
            "id" : 36619,
            "type" : "transport",
            "action" : "indices:data/write/delete/byquery",
            "status" : {    
              "total" : 6154,
              "updated" : 0,
              "created" : 0,
              "deleted" : 3500,
              "batches" : 36,
              "version_conflicts" : 0,
              "noops" : 0,
              "retries": 0,
              "throttled_millis": 0
            },
            "description" : ""
          }
        }
      }
    }
  }
  ```
  * 这个对象包含实际状态。他就像一个total字段的重要补充的响应json。`total`是reindex预期执行操作的全部数量。通过附加的`updated`，`created`和`deleted`字段，你可以评估这个进展。在他们的和等于total字段时，这个请求将结束
  * 你可以直接使用task查看：
  ```
  GET /_tasks/taskId:1
  ```
  * 使用集成了`wait_for_completion=false`的API的优势是返回任务完成状态。如果task已经完成，设置了`wait_for_completion=false`，那么将会返回`results`或者一个`error`字段。这个特性的消耗时`wait_for_completion=false`在`.tasks/task/${taskId}`创建了文档。由你来删除那个文档
* Works with the Cancel Task API
  * 任何Delete By Query可以使用Task Cancel API取消：
  ```
  POST _tasks/task_id:1/_cancel
  ```
  * task_id可以用前面的tasks API查找
  * 取消应该很快发生，但是可能需要花费几秒。任务状态API将继续列出，直到他被唤醒并取消自己
* Rethrottling
  * `requests_per_second`的值可以在一个运行的delete by query上使用`_rethrottle` API改变：
  ```
  POST _delete_by_query/task_id:1/_rethrottle?requests_per_second=-1
  ```
  * `task_id`可以使用上面的tasks API寻找
  * 就像在`_delete_by_query` API中可以设定`requests_per_second`可以设置为`-1`来禁用限制，或者任何正值如`1.7`或`12`来限制到该登记。Rethrottling加速是直接生效的，而减速将在完成当前批之后。这需要预防scroll超时
* Slicing
  * Delete-by-query支持Sliced Scroll并行删除处理。并行可以提高效率并且提供一个方便的方式来分割请求为更小的部分
  * Manually slicing
    * 手动分割一个delete-by-query通过提供一个slice id和每个请求slices的总数：
    ```
    POST twitter/_delete_by_query
    {
      "slice": {
        "id": 0,
        "max": 2
      },
      "query": {
        "range": {
          "likes": {
            "lt": 10
          }
        }
      }
    }
    POST twitter/_delete_by_query
    {
      "slice": {
        "id": 1,
        "max": 2
      },
      "query": {
        "range": {
          "likes": {
            "lt": 10
          }
        }
      }
    }
    ```
    * 可以这样验证工作：
    ```
    GET _refresh
    POST twitter/_search?size=0&filter_path=hits.total
    {
      "query": {
        "range": {
          "likes": {
            "lt": 10
          }
        }
      }
    }
    ```
    * 可以在结果中看到`total`像这样：
    ```
    {
      "hits": {
        "total": 0
      }
    }
    ```
  * Automatic slicing
    * 你同样可以让`delete-by-query`使用Sliced Scroll用`_uid`分割，自动并行化。使用`slices`到指定值来分割使用：
    ```
    POST twitter/_delete_by_query?refresh&slices=5
    {
      "query": {
        "range": {
          "likes": {
            "lt": 10
          }
        }
      }
    }
    ```
    * 你可以像这样进行验证：
    ```
    POST twitter/_search?size=0&filter_path=hits.total
    {
      "query": {
        "range": {
          "likes": {
            "lt": 10
          }
        }
      }
    }
    ```
    * 在结果中可以看到`total`：
    ```
    {
      "hits": {
        "total": 0
      }
    }
    ```
    * 设置`slices`为`auto`将让Elasticsearch选择使用的分割数量。这个设置将对每个分片使用一个slice，一直到一个确定的限制。如果有多个源索引，slices的数量将会基于分片最少的索引
    * 使用上面片段添加`slices`到`_delete_by_query`将自动化手动过程，创建子请求，意味着有一些转变：
      * 你可以在Tasks APIs里查看这些请求。这些子请求是带`slices`请求的任务的"child"任务
      * 获取这个带`slices`的请求的task状态仅包含slices完成状态
      * 这些子请求是对于像cancellation和rethrottling等是独立可寻址的
      * Rethrottling带`slices`的请求将rethrottle未结束的子请求比例
      * Canceling带slices的请求将会取消每个子请求
      * 由于slices的性质，每个子请求不会有文档分配偏好。所有文档将可被寻址，但是一些slices可能比其他的更大。预期大的slices有更均匀的分布
      * 参数如`requests_per_second`和`size`在一个带`slices`的请求中，将会分发部分到每个子请求。结合上面的关于分布不均匀的点，你应该得出结论使用带slices的size时，可能不会产生一个精确size被`_delete_by_query`的文档
      * 虽然几乎是同时发生的，每个子请求有一个略有不同的源索引的快照
  * Picking the number of slices
    * 如果自动分割，设置`slices`为`auto`会为大多数索引选择一个合理的数值。如果你手动分割或以别的方式调整自动分割，使用这些引导
    * query在slices数值等于索引中分片数量时执行效率最高。如果数值很大，（如500）选择一个更小的数值，因为过多的slices将会损害性能。设置slices大于分片数量通常不会提高效率，并且会加重负荷
    * Delete performance 通过number of slices线性调整可用资源
    * 无论是query或delete行为运行时依赖被重新索引的文档和集群资源控制
## Update API
* update API 允许基于一个提供的脚本更新文档。这个操作从索引中获取文档（按分片排列），运行脚本（使用脚本语言选项和参数），将结果重新索引（同样允许删除或忽略操作）。这使用版本确保在get和reindex之间没有发生更新
* 注意，这个操作仍然意味着完全重新索引这个文档，这只是移除了一些网络来回，减少了在get和index之间的版本冲突几率。这个特性要工作需要开启`_source`字段
* 例如，索引一个简单文档：
```
PUT test/_doc/1
{
    "counter" : 1,
    "tags" : ["red"]
}
```
* Scripted updates
  * 现在，我们可以执行一个将会增加counter的脚本：
  ```
  POST test/_doc/1/_update
  {
      "script" : {
          "source": "ctx._source.counter += params.count",
          "lang": "painless",
          "params" : {
              "count" : 4
          }
      }
  }
  ```
  * 我们可以添加一个tag到tags列表中（注意，如果tag已经存在了，他将仍然添加他，因为他是一个list）：
  ```
  POST test/_doc/1/_update
  {
      "script" : {
          "source": "ctx._source.tags.add(params.tag)",
          "lang": "painless",
          "params" : {
              "tag" : "blue"
          }
      }
  }
  ```
  * 添加到`_source`中，下面的变量可以通过`ctx` map使用：`_index`，`_type`，`_id`，`_version`，`_routing`和`_now`（当前时间戳）
  * 我们同样天天加一个新的字段到document中：
  ```
  POST test/_doc/1/_update
  {
      "script" : "ctx._source.new_field = 'value_of_new_field'"
  }
  ```
  * 或者从document移除一个字段
  ```
  POST test/_doc/1/_update
  {
      "script" : "ctx._source.remove('new_field')"
  }
  ```
  * 另外，我们甚至可以在操作执行时改变操作。例如如果tags字段包含green时删除文档，否则不进行操作（noop）：
  ```
  POST test/_doc/1/_update
  {
      "script" : {
          "source": "if (ctx._source.tags.contains(params.tag)) { ctx.op = 'delete' } else { ctx.op = 'none' }",
          "lang": "painless",
          "params" : {
              "tag" : "green"
          }
      }
  }
  ```
* Updates with a partial document
  * 这个更新API同样支持通过部分文档，它将被合并到存在的文档中（简单的循环合并，内部对象合并，代替核心的"keys/values"和数组）。要完全提到已存在的文档，index API应该被替代。下面部分更新添加一个新的字段到存在的文档中：
  ```
  POST test/_doc/1/_update
  {
      "doc" : {
          "name" : "new_name"
      }
  }
  ```
  * 如果`doc`和`script`同时指定了，那么`doc`将被忽略，最好将你的部分文档的字段对放到脚本自身中
* Detecting noop updates
  * 如果`doc`指定已存在的`_source`的值合并，默认的updates在侦测到没有任何改变时将不会任何东西，并且会返回"result":"noop"像这样：
  ```
  POST test/_doc/1/_update
  {
      "doc" : {
          "name" : "new_name"
      }
  }
  ```
  * 如果`name`在请求发送之前已经是new_name了，那么整个更新请求将被忽略。响应返回的`result`元素在请求被忽略时返回`noop`
  ```
  {
     "_shards": {
          "total": 0,
          "successful": 0,
          "failed": 0
     },
     "_index": "test",
     "_type": "_doc",
     "_id": "1",
     "_version": 6,
     "result": "noop"
  }
  ```
  * 你可以通过设置"detect_noop":false来禁用这个行为：
  ```
  POST test/_doc/1/_update
  {
      "doc" : {
          "name" : "new_name"
      },
      "detect_noop": false
  }
  ```
* Upserts
  * 如果文档不存在，`upsert`元素的内容将被插入为一个新的文档。如果文档已经存在，那么将执行脚本代替：
  ```
  POST test/_doc/1/_update
  {
      "script" : {
          "source": "ctx._source.counter += params.count",
          "lang": "painless",
          "params" : {
              "count" : 4
          }
      },
      "upsert" : {
          "counter" : 1
      }
  }
  ```
  * scripted_upsert
    * 如果你想要你的脚本无论文档是否存在都运行--例如脚本处理文档初始化而不是使用`upsert`元素--那么设置`scripted_upsert`为true：
    ```
    POST sessions/session/dh3sgudg8gsrgl/_update
    {
        "scripted_upsert":true,
        "script" : {
            "id": "my_web_session_summariser",
            "params" : {
                "pageViewEvent" : {
                    "url":"foo.com/bar",
                    "response":404,
                    "time":"2014-01-01 12:32"
                }
            }
        },
        "upsert" : {}
    }
    ```
  * doc_as_upsert
    * 代替发送部分`doc`附加一个`upsert`文档，设置`doc_as_upsert`为true将使用`doc`内容作为`upsert`的值：
    ```
    POST test/_doc/1/_update
    {
        "doc" : {
            "name" : "new_name"
        },
        "doc_as_upsert" : true
    }
    ```
* Parameters
  * 更新操作支持下面query-string参数：
  |||
  |-|-|
  |retry_on_conflict||
  