%module JNISharedMemIface

%include "stdint.i"
%include "std_vector.i"
%include "std_string.i"

%{
 #include "SharedMemIface.hpp"
%}

namespace std {
    %template(VectorInt8) vector<int8_t>;
};

class SharedMemIface {
public:
    SharedMemIface(const std::string& shmem_name_, size_t shmem_size_);
    ~SharedMemIface();
    std::string print_signals();
    int64_t get_signal_handle(const std::string& handle_name);
    std::vector<int8_t> read(int64_t handle);
    int64_t read64(int64_t handle);
    int32_t read32(int64_t handle);
    void write(int64_t handle, const std::vector<int8_t>& data);
    void write64(int64_t handle, int64_t data);
    void write32(int64_t handle, int32_t data);
    void sleep(int64_t sleep_cycles);
    void eval();
    bool error_happened();
    std::string error_string();
    void close();
};
