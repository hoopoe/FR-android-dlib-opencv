#include <iostream>
#include <dlib/dnn.h>
#include <dlib/gui_widgets.h>
#include <dlib/clustering.h>
#include <dlib/string.h>
#include <dlib/image_io.h>
#include <dlib/image_processing/frontal_face_detector.h>
#include <dlib/image_saver/save_png.h>
#include "sqlite_orm.h"

#include <experimental/filesystem>
namespace filesys = std::experimental::filesystem;

using namespace dlib;
using namespace std;

template <template <int, template<typename>class, int, typename> class block, int N, template<typename>class BN, typename SUBNET>
using residual = add_prev1<block<N, BN, 1, tag1<SUBNET>>>;

template <template <int, template<typename>class, int, typename> class block, int N, template<typename>class BN, typename SUBNET>
using residual_down = add_prev2<avg_pool<2, 2, 2, 2, skip1<tag2<block<N, BN, 2, tag1<SUBNET>>>>>>;

template <int N, template <typename> class BN, int stride, typename SUBNET>
using block = BN<con<N, 3, 3, 1, 1, relu<BN<con<N, 3, 3, stride, stride, SUBNET>>>>>;

template <int N, typename SUBNET> using ares = relu<residual<block, N, affine, SUBNET>>;
template <int N, typename SUBNET> using ares_down = relu<residual_down<block, N, affine, SUBNET>>;

template <typename SUBNET> using alevel0 = ares_down<256, SUBNET>;
template <typename SUBNET> using alevel1 = ares<256, ares<256, ares_down<256, SUBNET>>>;
template <typename SUBNET> using alevel2 = ares<128, ares<128, ares_down<128, SUBNET>>>;
template <typename SUBNET> using alevel3 = ares<64, ares<64, ares<64, ares_down<64, SUBNET>>>>;
template <typename SUBNET> using alevel4 = ares<32, ares<32, ares<32, SUBNET>>>;

using anet_type = loss_metric<fc_no_bias<128, avg_pool_everything<
    alevel0<
    alevel1<
    alevel2<
    alevel3<
    alevel4<
    max_pool<3, 3, 2, 2, relu<affine<con<32, 7, 7, 2, 2,
    input_rgb_image_sized<150>
    >>>>>>>>>>>>;

std::vector<std::string> getAllFilesInDir(const std::string &dirPath, const std::vector<std::string> acceptExt = {})
{
    std::vector<std::string> listOfFiles;
    try {
        if (filesys::exists(dirPath) && filesys::is_directory(dirPath))
        {
            filesys::recursive_directory_iterator iter(dirPath);
            filesys::recursive_directory_iterator end;
            while (iter != end)
            {
                if (filesys::is_regular_file(iter->path()) &&
                    (std::find(acceptExt.begin(), acceptExt.end(), iter->path().extension()) != acceptExt.end()))
                {
                    listOfFiles.push_back(iter->path().string());
                }
                error_code ec;
                iter.increment(ec);
                if (ec) {
                    std::cerr << "Error While Accessing : " << iter->path().string() << " :: " << ec.message() << '\n';
                }
            }
        }
    }
    catch (std::system_error & e)
    {
        std::cerr << "Exception :: " << e.what();
    }
    return listOfFiles;
}

std::string remove_extension(const std::string& filename) {
    size_t lastdot = filename.find_last_of(".");
    if (lastdot == std::string::npos) return filename;
    return filename.substr(0, lastdot);
}

typedef struct
{
    int id;
    std::string firstName;
    std::string lastName;
    std::string vector; //todo: custom type
} Person;

inline auto initStorage(const std::string &path) {
    using namespace sqlite_orm;
    return make_storage(path, make_table("persons",
        make_column("id",
            &Person::id,
            autoincrement(),
            primary_key()),
        make_column("first_name",
            &Person::firstName),
        make_column("last_name",
            &Person::lastName),
        make_column("vector",
            &Person::vector)));
};

using Storage = decltype(initStorage(""));
static std::shared_ptr<Storage> db;

matrix<float, 0, 1> string_to_fvector(const string& s) {
    std::string token;
    std::vector<float> res;
    std::istringstream tokenStream(s);
    while (std::getline(tokenStream, token, ',')) {
        res.push_back(std::stof(token));
    }
    matrix<float, 0, 1> face_vector = mat(res);
    return face_vector;
}

std::string fvector_to_string(matrix<float, 0, 1> face_desc) {
    std::vector<float> v(face_desc.begin(), face_desc.end());
    std::stringstream ss;
    for (size_t i = 0; i < v.size(); ++i)
    {
        if (i != 0)
            ss << ",";
        ss << v[i];
    }
    std::string s = ss.str();
    return s;
}

void dump_to_db(std::string chippedDir)
{
    frontal_face_detector detector = get_frontal_face_detector();
    shape_predictor sp;
    deserialize("shape_predictor_5_face_landmarks.dat") >> sp;
    anet_type net;
    deserialize("dlib_face_recognition_resnet_model_v1.dat") >> net;

    std::vector<std::string> listOfFiles = getAllFilesInDir(chippedDir, { ".jpg", ".JPG", ".png", ".PNG" });

    for (auto filepath : listOfFiles) {
        std::cout << filepath << std::endl;

        matrix<rgb_pixel> img;
        load_image(img, filepath);
        std::vector<matrix<rgb_pixel>> faces;

        for (auto face : detector(img))
        {
            auto shape = sp(img, face);
            matrix<rgb_pixel> face_chip;
            extract_image_chip(img, get_face_chip_details(shape, 150, 0.25), face_chip);
            faces.push_back(move(face_chip));
        }

        std::vector<matrix<float, 0, 1>> face_descriptors = net(faces);
        std::string filename = filesys::path(filepath).filename().string();
        std::string filename_wo_ext = remove_extension(filename);
        for (size_t i = 0; i < face_descriptors.size(); ++i)
        {
            string v = fvector_to_string(face_descriptors[i]);
            Person p{ -1, filename_wo_ext, "Undefined", v};
            auto insertedId = db->insert(p);
        }
    }
}

int main(int argc, char** argv)
{
    using namespace sqlite_orm;
    db = std::make_shared<Storage>(initStorage("people.db"));

    string dir = argv[1];
    dump_to_db(dir);
    return 0;
}