BUILD_DIR = ./build
VCD_DIR = ./vcd

RPJ = dcache
VCDS = $(shell find . -type f -name "*.vcd")

test:
	@./mill -i $(RPJ).test

verilog:
	@./mill -i $(RPJ).runMain Elaborate --target-dir $(BUILD_DIR)

vcd:
	@./mill -i $(RPJ).test.testOnly CacheTest -- -DemitVcd=1
	@$(shell mkdir -p $(VCD_DIR))
	@cp $(VCDS) $(VCD_DIR)/

clean:
	rm -rf $(BUILD_DIR) $(VCD_DIR)

.PHONY: test verilog vcd clean