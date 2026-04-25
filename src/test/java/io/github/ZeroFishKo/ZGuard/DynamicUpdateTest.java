package io.github.ZeroFishKo.ZGuard;

import io.github.ZeroFishKo.ZGuard.config.ValidatorConfig;
import io.github.ZeroFishKo.ZGuard.core.YmlConfigBinder;
import io.github.ZeroFishKo.ZGuard.core.YmlRuleLoader;
import io.github.ZeroFishKo.ZGuard.core.ZGuardValidator;
import io.github.ZeroFishKo.ZGuard.impl.RequiredValidator;
import io.github.ZeroFishKo.ZGuard.impl.Payment.PaymentCodeValidator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class DynamicUpdateTest {

    public static class PaymentRequest {
        // 仅用于绑定注解，这里简化处理，直接用手动绑定
    }

    public static void main(String[] args) throws Exception {
        // 1. 初始化校验器
        ValidatorConfig config = new ValidatorConfig() {
            @Override
            public String[] getCoreFields() {
                return new String[]{"paymentCode"};
            }
        };
        ZGuardValidator validator = new ZGuardValidator(config, "test_salt");

        // 注册所需校验器
        validator.registerValidators(Arrays.asList(
                new RequiredValidator(),
                new PaymentCodeValidator()
        ));
        // 手动绑定字段校验器
        validator.bindFieldValidators("paymentCode", Arrays.asList("required", "paymentCode"));

        // 2. 创建 YmlConfigBinder 并加载初始配置
        YmlConfigBinder binder = new YmlConfigBinder();
        // 3. 启动 YmlRuleLoader，它会每秒检查一次文件变化并自动更新
        try (YmlRuleLoader loader = new YmlRuleLoader(validator, "validator-rules.yml", binder)) {
            // 给一点时间让 loader 完成第一次加载
            Thread.sleep(2000);

            System.out.println("动态更新测试已启动，请修改 validator-rules.yml 文件中的 codeLength 值，然后观察校验结果。");
            System.out.println("当前规则：支付码长度应为 6 位。");

            // 循环等待用户输入，以便观察变化
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("\n输入一个支付码进行校验（输入 q 退出）：");
                String input = scanner.nextLine();
                if ("q".equalsIgnoreCase(input)) {
                    break;
                }
                Map<String, Object> data = new HashMap<>();
                data.put("paymentCode", input);
                boolean valid = validator.validate(data).isSuccess();
                System.out.println("校验结果：" + (valid ? "通过" : "失败 - " + validator.validate(data).getErrorMsg()));
            }
        }
    }
}