BUILD_DIR = ./build

RPJ = dcache

test:
	@./mill -i $(RPJ).test

verilog:
	@./mill -i $(RPJ).runMain Elaborate --target-dir $(BUILD_DIR)

clean:
	rm -rf $(BUILD_DIR)

.PHONY: test verilog clean