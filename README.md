# ZGuard Validator

**高并发轻量级校验框架** – 支持 YAML 动态规则、Web3 安全模块、防篡改哈希、XSS 清洗，自动适配 JDK 8/17/21 虚拟线程。

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.zerofishko/zguard-validator)](https://central.sonatype.com/artifact/io.github.zerofishko/zguard-validator)
[![Java Version](https://img.shields.io/badge/Java-8%2B-green.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)

---

## 🚀 核心特性

- **轻量高性能** – 预编译校验步骤，零反射运行时开销，单次校验 < 0.3 µs
- **动态规则** – YAML 配置热更新，无需重启应用
- **Web3 安全** – 内置以太坊地址、链 ID、签名、风险地址校验器
- **XSS 防御** – 黑名单快速过滤 或 OWASP 编码，自动清洗输入
- **防篡改哈希** – 支持 SimpleHash / HMAC-SHA256，保护核心字段
- **高并发批量** – 根据 JDK 版本自动使用虚拟线程 (JDK 21+) 或传统线程池，支持快速失败
- **注解绑定** – `@ValidateWith` 声明字段校验规则
- **国际化** – 内置多语言错误消息支持
- **JDK 多版本兼容** – 同一 Jar 包运行于 JDK 8/17/21（编译需 JDK21，运行向下兼容）

---

## 📦 快速开始

### 1. Maven 依赖引入

```xml
<dependency>
    <groupId>io.github.zerofishko</groupId>
    <artifactId>zguard-validator</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 实体类注解绑定校验

```java
public class PaymentRequest {
    @ValidateWith({"required", "web3Wallet", "riskAddr"})
    private String walletAddr;

    @ValidateWith({"required", "paymentCode"})
    private String paymentCode;

    @ValidateWith({"required", "range"})
    private Double transferAmount;

    @ValidateWith({"required", "web3ChainId"})
    private String chainId;
}
```

### 3. 初始化框架并执行校验

```java
ZGuardValidator validator = new ZGuardValidator();

// 注册内置校验器
validator.registerValidators(Arrays.asList(
    new RequiredValidator(),
    new Web3WalletValidator(),
    new PaymentCodeValidator(),
    new RangeValidator(),
    new Web3ChainIdValidator(),
    new RiskAddrValidator()
));

// 绑定注解规则
validator.bindAnnotations(PaymentRequest.class);

// 构造业务数据
Map<String, Object> data = new HashMap<>();
data.put("walletAddr", "0x1234567890abcdef1234567890abcdef12345678");
data.put("paymentCode", "12345678");
data.put("chainId", "56");
data.put("transferAmount", 500.0);

// 执行全局校验
ValidateResult result = validator.validate(data);
if (result.isSuccess()) {
    System.out.println("校验通过");
} else {
    System.out.println(result.getFieldName() + " : " + result.getErrorMsg());
}
```

---

## ⚙️ 内置校验器清单

| 校验器 Key | 功能说明 | 可配置参数 |
|------------|----------|------------|
| `required` | 非空校验（字符串/集合/Map/数组） | 无 |
| `length` | 字符/集合长度范围校验 | `min`, `max` |
| `range` | 数值区间校验（BigDecimal 兼容） | `min`, `max` |
| `regex` | 正则匹配（内置超时防卡死） | `regex`, `timeoutMs` |
| `paymentCode` | 固定长度支付码校验 | `codeLength` |
| `web3Wallet` | 以太坊标准地址格式校验 | 无 |
| `web3Amount` | 链上金额+小数精度限制 | `maxPrecision` |
| `web3ChainId` | 公链 ID 白名单校验 | `allowedChainIds` |
| `web3Signature` | Web3 交易签名格式校验 | 无 |
| `riskAddr` | 黑名单风险地址拦截 | `list` |

> 所有校验器支持通过代码动态传参 或 YAML 配置热修改。

---

## 🔧 自定义校验器

实现 `ValidatorHandler` 接口即可扩展自己的校验逻辑。例如手机号校验器：

```java
public class PhoneValidator implements ValidatorHandler {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    @Override
    public boolean validate(Object value) {
        return value instanceof String && PHONE_PATTERN.matcher((String) value).matches();
    }

    @Override
    public String getValidatorKey() {
        return "phone";
    }

    @Override
    public int getPriority() {
        return 3; // 优先级可选
    }
}
```

注册并使用：

```java
validator.registerValidator(new PhoneValidator());
validator.bindFieldValidators("mobile", Arrays.asList("required", "phone"));
```

---

## 📦 批量校验

处理多条数据时可显著提升吞吐量，框架自动适配 JDK 版本并发模型。

```java
List<Map<String, Object>> dataList = new ArrayList<>();
// 准备多条数据...

// 批量校验（自动使用虚拟线程或线程池）
List<ValidateResult> results = validator.batchValidate(dataList);

// 快速失败模式下，遇到第一个失败会立即终止剩余校验
for (ValidateResult res : results) {
    if (!res.isSuccess()) {
        System.out.println("失败字段：" + res.getFieldName() + "，原因：" + res.getErrorMsg());
        break;
    }
}
```

可通过 `validator.setBatchSize(200)` 调整每批并发数量。

---

## 🧪 YAML 动态规则 & 热更新

在项目 `classpath` 根目录新建 `validator-rules.yml`：

```yaml
# 全局校验器参数
validatorParams:
  paymentCode:
    codeLength: 8
  range:
    min: 100.0
    max: 100000.0
  web3ChainId:
    allowedChainIds: [56, 97, 1000, 137]
  regex:
    regex: "^[A-Za-z0-9]+$"
    timeoutMs: 50

# 全局字段校验绑定
fieldBindRules:
  walletAddr: ["required", "web3Wallet", "riskAddr"]
  paymentCode: ["required", "paymentCode"]
  transferAmount: ["required", "range"]
  chainId: ["required", "web3ChainId"]

# 风险地址黑名单
riskLists:
  riskWalletAddrs:
    list: ["0x1234dead...", "0x5678beef..."]
```

加载配置并启动热更新：

```java
YmlConfigBinder binder = new YmlConfigBinder();
binder.loadYml(getResourceAsStream("validator-rules.yml"));
binder.bindAllValidators(validator.getValidatorRegistry());

// 每秒监听配置文件变更，自动热加载
YmlRuleLoader loader = new YmlRuleLoader(validator, "validator-rules.yml", binder, 1000);
```

---

## 🛡️ 防篡改哈希保护

对核心字段进行哈希校验，防止中间人篡改：

```java
ValidatorConfig config = new ValidatorConfig() {
    @Override
    public String[] getCoreFields() {
        return new String[]{"walletAddr", "amount"};
    }
    @Override
    public HashStrategy getHashStrategy() {
        return new HmacSha256Strategy(); // 可选：SimpleHashStrategy
    }
};
ZGuardValidator validator = new ZGuardValidator(config, "your-secret-salt");

// 客户端生成签名哈希
String hash = hashStrategy.hash(walletAddr, salt);
data.put("walletAddr_hash", hash);
```

哈希不匹配时自动拦截，返回「数据被篡改」错误。

---

## 🌍 国际化支持

在 classpath 下创建 `messages_zh_CN.properties` 等资源文件，例如：

```properties
validator.required=字段【{0}】不能为空
validator.paymentCode=支付码必须为{0}位数字
```

使用自定义 `MessageSource` 注入：

```java
MessageSource messageSource = new ResourceBundleMessageSource("messages");
ZGuardValidator validator = new ZGuardValidator(config, "salt", messageSource);
```

在校验失败时会自动根据 `Locale` 返回本地化消息。

---

## ⚙️ ValidatorConfig 配置选项

可以通过实现 `ValidatorConfig` 接口来定制框架行为，默认值如下：

| 方法 | 说明 | 默认值 |
|------|------|--------|
| `isBatchFastFail()` | 批量校验时遇到第一个失败即终止 | `true` |
| `isCombineFastFail()` | 单个字段多个校验器时快速失败 | `true` |
| `getCoreFields()` | 必须存在的核心字段数组 | `[]` |
| `getFailureMode()` | 失败处理模式（目前仅支持 `QUICK_FAIL`） | `QUICK_FAIL` |
| `getUpdateDelayMs()` | 动态配置更新后的等待延迟（毫秒） | `100` |
| `isLogXssClean()` | 是否记录 XSS 清洗日志 | `false` |
| `getXssMode()` | XSS 清洗模式 | `XssMode.BLACKLIST` |
| `getHashStrategy()` | 签名哈希策略 | `SimpleHashStrategy` |
| `getRegexTimeoutMs()` | 正则校验超时（毫秒） | `100` |

---

## 🔥 性能基准测试

测试环境：Intel i7-12700H、32GB 内存、Windows 11、JDK 21.0.9

### 单条数据校验

| 业务场景 | 吞吐量(ops/s) | 平均延迟(µs) |
|----------|---------------|--------------|
| 常规完整校验 | 3,782,819 | 0.26 |

### 批量并发校验

| 批处理数量 | 无效数据占比 | 单条平均耗时(µs) |
|------------|--------------|------------------|
| 10 | 0% | 0.206 |
| 10 | 20% | 0.209 |
| 10 | 50% | 0.238 |
| 100 | 0% | 0.621 |
| 100 | 20% | 0.593 |
| 100 | 50% | 0.605 |
| 1000 | 0% | 3.799 |
| 1000 | 20% | 4.213 |
| 1000 | 50% | 4.159 |

### 不同复杂度请求延迟

| 请求类型 | 校验字段数 | 平均延迟(µs) |
|----------|------------|--------------|
| simple | 2 | 0.178 |
| medium | 3 | 0.184 |
| complex | 4 | 0.133 |

### 核心功能性能指标

| 操作类型 | 平均延迟(µs) |
|----------|--------------|
| 深层参数路径提取(深度30) | 1.295 |
| 10KB 文本 XSS 清洗 | 17.20 |
| YAML 配置动态刷新 | 0.189 |
| 20 线程高并发校验 | 0.768 |
| HMAC-SHA256 哈希校验 | 0.652 |
| 缺失字段快速失败拦截 | 0.015 |

---

## 📌 注意事项

- **正则校验超时**：对于复杂正则表达式建议设置合理的 `timeoutMs`，防止 ReDoS 攻击。
- **风险列表为空**：`riskAddr` 校验器在列表为空时会放行所有地址。
- **虚拟线程**：仅在 JDK 21+ 运行时启用，低版本自动回退到传统线程池。
- **编译与运行 JDK**：发布的 jar 使用 JDK 21 编译，但可在 JDK 8/17/21 上运行（通过反射适配虚拟线程）。
- **哈希策略**：生产环境推荐使用 `HmacSha256Strategy`，`SimpleHashStrategy` 仅用于性能测试或不敏感场景。

---

## 🤝 项目支持与贡献

- 欢迎提交 Issue、Pull Request 优化框架能力
- 开源仓库：[https://github.com/ZerofishKO/zguard-validator](https://github.com/ZerofishKO/zguard-validator)
- 技术咨询 & 问题反馈：2627090410@qq.com

---

## 📄 License

Apache License 2.0 – 详见 [LICENSE](LICENSE) 文件。
```
